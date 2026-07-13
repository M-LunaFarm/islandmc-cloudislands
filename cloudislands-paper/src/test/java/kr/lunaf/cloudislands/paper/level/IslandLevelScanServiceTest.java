package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RuntimeActionView;
import kr.lunaf.cloudislands.coreclient.RuntimeCommandClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomBlockKeyService;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import kr.lunaf.cloudislands.paper.platform.world.PaperWorldGateway;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class IslandLevelScanServiceTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000153");

    @Test
    void deduplicatesConcurrentRequestsAndReplacesAfterTickBatches() {
        TestRuntimeClient client = new TestRuntimeClient();
        ManualTickScheduler scheduler = new ManualTickScheduler();
        IslandLevelScanService service = service(client, scheduler, world(0, 1));

        CompletableFuture<Void> first = service.rescanIsland(ISLAND_ID);
        CompletableFuture<Void> second = service.rescanIsland(ISLAND_ID);

        assertSame(first, second);
        scheduler.runUntil(first);
        first.join();
        assertEquals(1, client.replacements);
        assertEquals(Map.of(), client.lastReplacement);
        assertEquals(0, service.inFlightCount());
    }

    @Test
    void serializesPriorDeltasBeforeAuthoritativeReplacement() {
        TestRuntimeClient client = new TestRuntimeClient();
        client.pendingDelta = new CompletableFuture<>();
        ManualTickScheduler scheduler = new ManualTickScheduler();
        IslandLevelScanService service = service(client, scheduler, world(0, 1));

        service.recordBlockDelta(ISLAND_ID, "minecraft:stone", 1L);
        CompletableFuture<Void> scan = service.rescanIsland(ISLAND_ID);
        scheduler.runUntilTaskCancelled();

        assertFalse(scan.isDone());
        client.pendingDelta.complete(new RuntimeActionView(true, "ok"));
        scan.join();
        assertEquals(List.of("delta", "replace"), client.writeOrder);
        assertEquals(0, service.mutationStateCount());
    }

    @Test
    void refusesToOverwriteCountsWhenBlocksChangeMidScan() {
        TestRuntimeClient client = new TestRuntimeClient();
        ManualTickScheduler scheduler = new ManualTickScheduler();
        IslandLevelScanService service = service(client, scheduler, world(0, 20_000));

        CompletableFuture<Void> scan = service.rescanIsland(ISLAND_ID);
        scheduler.runOneTick();
        service.recordBlockDelta(ISLAND_ID, "minecraft:stone", 1L);
        scheduler.runUntil(scan);

        assertThrows(RuntimeException.class, scan::join);
        assertEquals(0, client.replacements);
    }

    @Test
    void shutdownCancelsInFlightScans() {
        TestRuntimeClient client = new TestRuntimeClient();
        ManualTickScheduler scheduler = new ManualTickScheduler();
        IslandLevelScanService service = service(client, scheduler, world(0, 20_000));

        CompletableFuture<Void> scan = service.rescanIsland(ISLAND_ID);
        scheduler.runOneTick();
        service.stop();

        assertThrows(RuntimeException.class, scan::join);
        assertEquals(0, service.inFlightCount());
        assertEquals(0, client.replacements);
    }

    private static IslandLevelScanService service(TestRuntimeClient client, ManualTickScheduler scheduler, World world) {
        ActiveIslandRegistry registry = new ActiveIslandRegistry();
        registry.activated(new kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler.ActivationResult(
            true, "ACTIVE", ISLAND_ID, "world", 0, 0, 0, 0, 2, 1L, 1L,
            "", 0L, "", 0L, "", 0L, "", 0L, "test"
        ));
        PaperWorldGateway worlds = _worldName -> world;
        return new IslandLevelScanService(
            proxy(Plugin.class),
            () -> registry,
            client,
            worlds,
            CustomBlockKeyService.vanillaOnly(),
            scheduler
        );
    }

    private static World world(int minHeight, int maxHeight) {
        Block block = proxy(Block.class, (method, _args) -> method.getName().equals("getType") ? Material.AIR : defaultValue(method.getReturnType()));
        return proxy(World.class, (method, _args) -> switch (method.getName()) {
            case "getMinHeight" -> minHeight;
            case "getMaxHeight" -> maxHeight;
            case "getBlockAt" -> block;
            case "getNearbyEntities" -> List.of();
            default -> defaultValue(method.getReturnType());
        });
    }

    private static <T> T proxy(Class<T> type) {
        return proxy(type, (method, _args) -> defaultValue(method.getReturnType()));
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (_proxy, method, args) -> invocation.invoke(method, args)));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        return 0.0D;
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    private static final class ManualTickScheduler implements IslandLevelScanService.TickScheduler {
        private Runnable task;
        private boolean cancelled;

        @Override
        public TaskHandle repeatEveryTick(Runnable task) {
            this.task = task;
            this.cancelled = false;
            return () -> cancelled = true;
        }

        private void runOneTick() {
            if (!cancelled && task != null) {
                task.run();
            }
        }

        private void runUntil(CompletableFuture<Void> future) {
            for (int tick = 0; tick < 50_000 && !future.isDone(); tick++) {
                runOneTick();
            }
        }

        private void runUntilTaskCancelled() {
            for (int tick = 0; tick < 50_000 && !cancelled; tick++) {
                runOneTick();
            }
        }
    }

    private static final class TestRuntimeClient implements CoreApiClient, RuntimeCommandClient {
        private final List<String> writeOrder = new ArrayList<>();
        private CompletableFuture<RuntimeActionView> pendingDelta;
        private int replacements;
        private Map<String, Long> lastReplacement = Map.of();

        @Override
        public CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta) {
            writeOrder.add("delta");
            return pendingDelta == null ? completed() : pendingDelta;
        }

        @Override
        public CompletableFuture<RuntimeActionView> replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
            writeOrder.add("replace");
            replacements++;
            lastReplacement = Map.copyOf(counts);
            return completed();
        }

        @Override
        public CompletableFuture<RuntimeActionView> publishHeartbeat(NodeHeartbeatRequest request) {
            return completed();
        }

        @Override
        public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
            return completed();
        }

        @Override
        public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage) {
            return completed();
        }

        private static CompletableFuture<RuntimeActionView> completed() {
            return CompletableFuture.completedFuture(new RuntimeActionView(true, "ok"));
        }
    }
}
