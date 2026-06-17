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
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
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

    private CreateIslandWorkflow workflow(InMemoryIslandRepository islands, InMemoryIslandRuntimeRepository runtimes, InMemoryPlayerProfileRepository profiles, InMemoryIslandJobPublisher jobs, InMemoryRouteTicketStore tickets) {
        return workflow(islands, runtimes, profiles, jobs, tickets, new RecordingEvents());
    }

    private CreateIslandWorkflow workflow(InMemoryIslandRepository islands, InMemoryIslandRuntimeRepository runtimes, InMemoryPlayerProfileRepository profiles, InMemoryIslandJobPublisher jobs, InMemoryRouteTicketStore tickets, GlobalEventPublisher events) {
        return new CreateIslandWorkflow(
            islands,
            new InMemoryIslandMetadataRepository(),
            profiles,
            new InMemoryIslandTemplateRepository(),
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
