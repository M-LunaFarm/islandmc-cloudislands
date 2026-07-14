package kr.lunaf.cloudislands.paper.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

class ShardWorldPreloaderTest {
    @Test
    void resolvesWorldAndStartsAsyncChunkLoadsOnlyInsideGlobalScheduler() {
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        List<String> calls = new ArrayList<>();
        World world = world(calls);
        ShardWorldPreloader preloader = new ShardWorldPreloader(
            null,
            worldName -> {
                calls.add("world:" + worldName);
                return world;
            },
            scheduler(scheduled)
        );

        preloader.preload("ci_shard_001", 32, -16, 1);

        assertTrue(calls.isEmpty(), "Bukkit world state must not be read on the activation worker thread");
        scheduled.get().run();
        assertEquals("world:ci_shard_001", calls.getFirst());
        assertEquals(10, calls.size(), "one world lookup and a 3x3 asynchronous chunk preload are expected");
        assertTrue(calls.contains("chunk:1,-2"));
        assertTrue(calls.contains("chunk:3,0"));
    }

    private World world(List<String> calls) {
        return (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{World.class}, (proxy, method, args) -> {
            if (method.getName().equals("getChunkAtAsync") && args != null && args.length >= 2) {
                calls.add("chunk:" + args[0] + "," + args[1]);
                return CompletableFuture.completedFuture(null);
            }
            if (method.getName().equals("toString")) {
                return "test-world";
            }
            throw new UnsupportedOperationException(method.toString());
        });
    }

    private PlatformScheduler scheduler(AtomicReference<Runnable> scheduled) {
        return new PlatformScheduler() {
            @Override public TaskHandle runGlobal(Runnable task) { scheduled.set(task); return TaskHandle.noop(); }
            @Override public TaskHandle runAsync(Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle runForPlayer(UUID playerId, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle runForChunk(String worldKey, int chunkX, int chunkZ, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle repeatGlobal(Duration delay, Duration interval, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle repeatAsync(Duration delay, Duration interval, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public void close() { }
        };
    }
}
