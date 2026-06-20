package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.template.InMemoryIslandTemplateRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

class RoutingOrchestratorActivationTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000201");

    @Test
    void repeatedHomeRouteWhileIslandIsActivatingDoesNotPublishDuplicateActivation() {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository runtimes = new InMemoryIslandRuntimeRepository();
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        RoutingOrchestrator orchestrator = orchestrator(islands, runtimes, jobs, tickets);
        islands.createOwnedIsland(ISLAND, OWNER, "default", "owner-island");
        islands.setState(ISLAND, IslandState.INACTIVE_READY);
        runtimes.markInactive(ISLAND, 7L);

        RoutePreparationResult first = orchestrator.prepareHomeRoute(OWNER);
        RouteTicket firstTicket = tickets.findLatestForPlayer(OWNER).orElseThrow();
        RoutePreparationResult second = orchestrator.prepareHomeRoute(OWNER);

        assertEquals(202, first.status());
        assertEquals(RouteTicketState.PREPARING, firstTicket.state());
        assertEquals("island-2", firstTicket.targetNode());
        assertEquals(IslandState.ACTIVATING, runtimes.find(ISLAND).orElseThrow().state());
        assertEquals(409, second.status());
        assertTrue(second.body().contains("\"code\":\"ISLAND_PREPARING\""));
        assertEquals(firstTicket.ticketId(), tickets.findLatestForPlayer(OWNER).orElseThrow().ticketId());

        List<IslandJob> published = jobs.snapshot();
        assertEquals(1, published.size());
        IslandJob activation = published.getFirst();
        assertEquals(IslandJobType.ACTIVATE_ISLAND, activation.type());
        assertEquals(ISLAND, activation.islandId());
        assertEquals("island-2", activation.targetNode());
        assertEquals("8", activation.payload().get("fencingToken"));
    }

    @Test
    void concurrentHomeRoutesWithStaleRuntimeReadPublishOneActivationOnly() throws Exception {
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandRuntimeRepository delegate = new InMemoryIslandRuntimeRepository();
        BlockingRuntimeRepository runtimes = new BlockingRuntimeRepository(delegate, ISLAND, 2);
        InMemoryIslandJobPublisher jobs = new InMemoryIslandJobPublisher();
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(NOW, ZoneOffset.UTC));
        RoutingOrchestrator orchestrator = orchestrator(islands, runtimes, jobs, tickets);
        islands.createOwnedIsland(ISLAND, OWNER, "default", "owner-island");
        islands.setState(ISLAND, IslandState.INACTIVE_READY);
        delegate.markInactive(ISLAND, 7L);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<RoutePreparationResult> task = () -> orchestrator.prepareHomeRoute(OWNER);

            Future<RoutePreparationResult> first = executor.submit(task);
            Future<RoutePreparationResult> second = executor.submit(task);

            List<Integer> statuses = List.of(first.get(5, TimeUnit.SECONDS).status(), second.get(5, TimeUnit.SECONDS).status()).stream().sorted().toList();
            assertEquals(List.of(202, 409), statuses);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, jobs.snapshot().size());
        assertEquals(IslandJobType.ACTIVATE_ISLAND, jobs.snapshot().getFirst().type());
        assertEquals(1L, tickets.countsByState().get("PREPARING"));
        assertEquals(IslandState.ACTIVATING, delegate.find(ISLAND).orElseThrow().state());
        assertEquals(8L, delegate.find(ISLAND).orElseThrow().fencingToken());
    }

    private RoutingOrchestrator orchestrator(InMemoryIslandRepository islands, IslandRuntimeRepository runtimes, InMemoryIslandJobPublisher jobs, InMemoryRouteTicketStore tickets) {
        return new RoutingOrchestrator(
            new StaticNodeRegistry(List.of(node("island-2", "Island-2", 20, 120, 20.0, 0))),
            new NodeAllocator(Duration.ofSeconds(5)),
            tickets,
            islands,
            new InMemoryIslandMetadataRepository(),
            runtimes,
            new InMemoryIslandTemplateRepository(),
            jobs,
            new NoopEvents(),
            "island",
            Duration.ofSeconds(30),
            Duration.ofSeconds(120)
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
            Instant.now(),
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

    private static final class NoopEvents implements GlobalEventPublisher {
        @Override
        public void publish(String eventType, Map<String, String> fields) {
        }
    }

    private static final class BlockingRuntimeRepository implements IslandRuntimeRepository {
        private final IslandRuntimeRepository delegate;
        private final UUID blockedIslandId;
        private final int blockedReads;
        private final AtomicInteger reads = new AtomicInteger();
        private final CountDownLatch ready;

        private BlockingRuntimeRepository(IslandRuntimeRepository delegate, UUID blockedIslandId, int blockedReads) {
            this.delegate = delegate;
            this.blockedIslandId = blockedIslandId;
            this.blockedReads = blockedReads;
            this.ready = new CountDownLatch(blockedReads);
        }

        @Override
        public Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> find(UUID islandId) {
            Optional<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> snapshot = delegate.find(islandId);
            if (blockedIslandId.equals(islandId) && reads.incrementAndGet() <= blockedReads) {
                ready.countDown();
                try {
                    assertTrue(ready.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while coordinating stale runtime read", exception);
                }
            }
            return snapshot;
        }

        @Override
        public List<kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot> listByNode(String nodeId, int limit) {
            return delegate.listByNode(nodeId, limit);
        }

        @Override
        public boolean placementOccupied(String worldName, int cellX, int cellZ, UUID exceptIslandId) {
            return delegate.placementOccupied(worldName, cellX, cellZ, exceptIslandId);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, String targetWorld, int cellX, int cellZ) {
            return delegate.markActivating(islandId, targetNode, targetWorld, cellX, cellZ);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markActive(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long fencingToken) {
            return delegate.markActive(islandId, nodeId, worldName, cellX, cellZ, fencingToken);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markSaving(UUID islandId) {
            return delegate.markSaving(islandId);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markSaving(UUID islandId, long fencingToken) {
            return delegate.markSaving(islandId, fencingToken);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markInactive(UUID islandId) {
            return delegate.markInactive(islandId);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markInactive(UUID islandId, long fencingToken) {
            return delegate.markInactive(islandId, fencingToken);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markMigrating(UUID islandId, String targetNode) {
            return delegate.markMigrating(islandId, targetNode);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot markQuarantined(UUID islandId, String reason) {
            return delegate.markQuarantined(islandId, reason);
        }

        @Override
        public kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot setState(UUID islandId, IslandState state) {
            return delegate.setState(islandId, state);
        }

        @Override
        public Map<String, Long> countsByState() {
            return delegate.countsByState();
        }

        @Override
        public int markRecoveryRequiredForNode(String nodeId) {
            return delegate.markRecoveryRequiredForNode(nodeId);
        }
    }
}
