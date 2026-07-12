package kr.lunaf.cloudislands.coreservice.workflow;

import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateIslandWorkflowTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Test
    void createsIslandJobAndPreparingTicketForNewOwner() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        CreateIslandWorkflow workflow = workflow(islands, runtimes, profiles, jobs, tickets);

        CreateIslandResult result = workflow.create(OWNER, "default");

        assertTrue(result.accepted());
        assertEquals("CREATING", result.code());
        assertNotNull(result.island());
        assertEquals(IslandState.CREATING, result.island().state());
        assertEquals(Optional.of(result.island().islandId()), profiles.find(OWNER).primaryIslandId());
        assertEquals(RouteTicketState.PREPARING, result.ticket().state());
        assertEquals("island-2", result.ticket().targetNode());
        assertEquals("Island-2", result.ticket().payload().get("targetServerName"));
        assertEquals("ISLAND_HOME", result.ticket().payload().get("targetType"));

        List<IslandJob> published = jobs.snapshot();
        assertEquals(1, published.size());
        IslandJob job = published.getFirst();
        assertEquals(IslandJobType.CREATE_ISLAND, job.type());
        assertEquals(result.island().islandId(), job.islandId());
        assertEquals("island-2", job.targetNode());
        assertEquals("default", job.payload().get("templateId"));
        assertEquals(OWNER.toString(), job.payload().get("ownerUuid"));
        assertTrue(job.payload().containsKey("worldName"));
        assertTrue(job.payload().containsKey("cellX"));
        assertTrue(job.payload().containsKey("cellZ"));
    }

    @Test
    void rejectsDuplicateCreateBeforePublishingAnotherJobOrTicket() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        RecordingEvents events = new RecordingEvents();
        CreateIslandWorkflow workflow = workflow(islands, runtimes, profiles, jobs, tickets, events);

        CreateIslandResult first = workflow.create(OWNER, "default");
        CreateIslandResult second = workflow.create(OWNER, "default");

        assertTrue(first.accepted());
        assertFalse(second.accepted());
        assertEquals("ALREADY_HAS_ISLAND", second.code());
        assertEquals(1, jobs.snapshot().size());
        assertEquals(first.ticket().ticketId(), tickets.findLatestForPlayer(OWNER).orElseThrow().ticketId());
        assertTrue(events.contains("ROUTE_TICKET_FAILED", "reason", "ALREADY_HAS_ISLAND"));
    }

    @Test
    void createsDistinctDefaultIslandNamesForDifferentOwners() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        CreateIslandWorkflow workflow = workflow(islands, runtimes, profiles, jobs, tickets);

        CreateIslandResult first = workflow.create(OWNER, "default");
        CreateIslandResult second = workflow.create(UUID.fromString("00000000-0000-0000-0000-000000000202"), "default");

        assertTrue(first.accepted());
        assertTrue(second.accepted());
        assertTrue(first.island().name().startsWith("Island-"));
        assertTrue(second.island().name().startsWith("Island-"));
        assertFalse(first.island().name().equals(second.island().name()));
    }

    @Test
    void copiesTemplateBundleAndSpawnMetadataIntoCreateJobAndTicket() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        InMemoryIslandTemplateRepository templates = new InMemoryIslandTemplateRepository();
        templates.upsert(new IslandTemplateSnapshot(
            "hardcore",
            "Hardcore Island",
            "Hard mode starter",
            "challenge",
            true,
            "1.2.0",
            "cloudislands.template.hardcore",
            "NETHERRACK",
            17,
            "preview/hardcore.png",
            "templates/hardcore.tar.zst",
            "feedbeef",
            8192L,
            4,
            192,
            8.5D,
            96.0D,
            -7.5D,
            45.0F,
            10.0F,
            "arrival",
            "nether",
            "minecraft:nether_wastes",
            "RED",
            "0",
            "250",
            5,
            List.of("challenge"),
            java.time.Instant.EPOCH,
            java.time.Instant.EPOCH
        ));
        CreateIslandWorkflow workflow = workflow(islands, runtimes, profiles, jobs, tickets, new RecordingEvents(), templates);

        CreateIslandResult unapproved = workflow.create(OWNER, "hardcore");
        assertFalse(unapproved.accepted());
        assertEquals("PAID_TEMPLATE_SETTLEMENT_REQUIRED", unapproved.code());
        assertTrue(jobs.snapshot().isEmpty());

        CreateIslandResult result = workflow.create(OWNER, "hardcore", true);

        assertTrue(result.accepted());
        assertEquals(192, result.island().size());
        IslandJob job = jobs.snapshot().getFirst();
        assertEquals("hardcore", job.payload().get("templateId"));
        assertEquals("templates/hardcore.tar.zst", job.payload().get("templateBundlePath"));
        assertEquals("feedbeef", job.payload().get("templateBundleChecksum"));
        assertEquals("4", job.payload().get("templateSchemaVersion"));
        assertEquals("arrival", job.payload().get("homeName"));
        assertEquals("8.5", job.payload().get("localX"));
        assertEquals("96.0", job.payload().get("localY"));
        assertEquals("-7.5", job.payload().get("localZ"));
        assertEquals("45.0", job.payload().get("yaw"));
        assertEquals("10.0", job.payload().get("pitch"));
        assertEquals("250", job.payload().get("creationCost"));
        assertEquals("arrival", result.ticket().payload().get("homeName"));
        assertEquals("8.5", result.ticket().payload().get("localX"));
        assertEquals("96.0", result.ticket().payload().get("localY"));
        assertEquals("-7.5", result.ticket().payload().get("localZ"));
    }

    @Test
    void failedQueuePublishFailsPreparingTicketAndCanRetrySameIsland() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        IslandJobPublisher failingJobs = _job -> {
            throw new IllegalStateException("queue unavailable");
        };

        CreateIslandResult failed = workflow(islands, runtimes, profiles, failingJobs, tickets).create(OWNER, "default");

        assertFalse(failed.accepted());
        assertEquals("JOB_QUEUE_UNAVAILABLE", failed.code());
        assertEquals(IslandState.ERROR_CREATING, failed.island().state());
        assertEquals(RouteTicketState.FAILED, tickets.findLatestForPlayer(OWNER).orElseThrow().state());

        InMemoryIslandJobPublisher recoveredJobs = new InMemoryIslandJobPublisher();
        CreateIslandResult retried = workflow(islands, runtimes, profiles, recoveredJobs, tickets).create(OWNER, "default");

        assertTrue(retried.accepted());
        assertEquals(failed.island().islandId(), retried.island().islandId());
        assertEquals(1, recoveredJobs.snapshot().size());
        assertEquals(RouteTicketState.PREPARING, retried.ticket().state());
    }

    @Test
    void failedCreateCannotBeRetriedWithAFreeOrDifferentTemplate() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        InMemoryIslandTemplateRepository templates = new InMemoryIslandTemplateRepository();
        IslandTemplateSnapshot defaultTemplate = templates.find("default").orElseThrow();
        templates.upsert(new IslandTemplateSnapshot(
            "starter", "Starter", "", "default", true, defaultTemplate.minNodeVersion(), "", "GRASS_BLOCK", 0,
            "", "", "", 0L, 3, 300, 0.5D, 100.0D, 0.5D, 180.0F, 0.0F, "default", "normal",
            "minecraft:plains", "BLUE", "0", "0", 0, List.of(), Instant.EPOCH, Instant.EPOCH
        ));
        IslandJobPublisher failingJobs = _job -> {
            throw new IllegalStateException("queue unavailable");
        };
        CreateIslandWorkflow failedWorkflow = workflow(islands, runtimes, profiles, failingJobs, tickets, new RecordingEvents(), templates);

        assertEquals("JOB_QUEUE_UNAVAILABLE", failedWorkflow.create(OWNER, "default").code());

        CreateIslandResult mismatch = workflow(islands, runtimes, profiles, new InMemoryIslandJobPublisher(), tickets, new RecordingEvents(), templates).create(OWNER, "starter");

        assertFalse(mismatch.accepted());
        assertEquals("FAILED_CREATE_TEMPLATE_MISMATCH", mismatch.code());
    }

    private CreateIslandWorkflow workflow(InMemoryIslandRepository islands, InMemoryIslandRuntimeRepository runtimes, InMemoryPlayerProfileRepository profiles, IslandJobPublisher jobs, InMemoryRouteTicketStore tickets) {
        return workflow(islands, runtimes, profiles, jobs, tickets, new RecordingEvents());
    }

    private CreateIslandWorkflow workflow(InMemoryIslandRepository islands, InMemoryIslandRuntimeRepository runtimes, InMemoryPlayerProfileRepository profiles, IslandJobPublisher jobs, InMemoryRouteTicketStore tickets, GlobalEventPublisher events) {
        return workflow(islands, runtimes, profiles, jobs, tickets, events, new InMemoryIslandTemplateRepository());
    }

    private CreateIslandWorkflow workflow(InMemoryIslandRepository islands, InMemoryIslandRuntimeRepository runtimes, InMemoryPlayerProfileRepository profiles, IslandJobPublisher jobs, InMemoryRouteTicketStore tickets, GlobalEventPublisher events, InMemoryIslandTemplateRepository templates) {
        return new CreateIslandWorkflow(
            islands,
            new InMemoryIslandMetadataRepository(),
            profiles,
            templates,
            new StaticNodeRegistry(List.of(node("island-2", "Island-2", 20, 120, 20.0, 1))),
            new NodeAllocator(Duration.ofSeconds(5)),
            runtimes,
            jobs,
            events,
            tickets,
            "island",
            Duration.ofSeconds(120),
            null
        );
    }

    private NodeLoad node(String nodeId, String velocityServerName, int players, int activeIslands, double mspt, int activationQueue) {
        return new NodeLoad(
            nodeId,
            "island",
            velocityServerName,
            "1.2.0",
            NodeState.READY,
            players,
            90,
            110,
            15,
            activeIslands,
            600,
            mspt,
            activationQueue,
            20,
            0.10,
            2048,
            8192,
            0,
            Instant.now().minusSeconds(1),
            true,
            "*"
        );
    }

    private record StaticNodeRegistry(List<NodeLoad> nodes) implements NodeRegistry {
        @Override
        public void heartbeat(NodeHeartbeatRequest request) {
        }

        @Override
        public boolean drain(String nodeId) {
            return false;
        }

        @Override
        public boolean shutdownSafe(String nodeId) {
            return false;
        }

        @Override
        public boolean undrain(String nodeId) {
            return false;
        }

        @Override
        public List<String> markStaleDown(Duration heartbeatTimeout) {
            return List.of();
        }

        @Override
        public List<NodeLoad> snapshot() {
            return nodes;
        }

        @Override
        public Optional<NodeLoad> find(String nodeId) {
            return nodes.stream().filter(node -> node.nodeId().equals(nodeId)).findFirst();
        }
    }

    private static final class RecordingEvents implements GlobalEventPublisher {
        private final List<Event> events = new ArrayList<>();

        @Override
        public void publish(String eventType, Map<String, String> fields) {
            events.add(new Event(eventType, Map.copyOf(fields)));
        }

        boolean contains(String eventType, String key, String value) {
            return events.stream()
                .filter(event -> event.type().equals(eventType))
                .anyMatch(event -> value.equals(event.fields().get(key)));
        }
    }

    private record Event(String type, Map<String, String> fields) {
    }
}
