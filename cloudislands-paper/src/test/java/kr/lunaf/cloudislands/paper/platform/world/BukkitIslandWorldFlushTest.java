package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import org.junit.jupiter.api.Test;

class BukkitIslandWorldFlushTest {
    @Test
    void productionFlushWaitsForChunkRegionWrites() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/BukkitIslandWorldFlush.java"));

        assertTrue(source.contains("world.save(true);"), "snapshot export must wait for Paper to flush region files");
    }

    @Test
    void shutdownOnGlobalThreadDrainsAsyncFlushWithoutDeadlockingPaper() throws Exception {
        AtomicBoolean primaryThread = new AtomicBoolean();
        AtomicInteger saves = new AtomicInteger();
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        CountDownLatch requestScheduled = new CountDownLatch(1);
        PlatformScheduler scheduler = scheduler(scheduled, requestScheduled);
        BukkitIslandWorldFlush flush = new BukkitIslandWorldFlush(
            scheduler,
            Duration.ofSeconds(1),
            primaryThread::get,
            _worldName -> saves.incrementAndGet()
        );
        ActiveIslandRegistry.ActiveIsland island = activeIsland();
        Thread periodicSave = Thread.ofVirtual().start(() -> {
            try {
                flush.flush(island, "AUTO");
            } catch (java.io.IOException error) {
                throw new RuntimeException(error);
            }
        });

        requestScheduled.await(1L, TimeUnit.SECONDS);
        assertNotNull(scheduled.get());
        primaryThread.set(true);
        flush.prepareShutdown(List.of(island));
        periodicSave.join(1_000L);

        assertFalse(periodicSave.isAlive(), "the async save must be released while onDisable still owns the global thread");
        assertEquals(1, saves.get(), "shutdown preparation must reuse the drained world flush");
        scheduled.get().run();
        assertEquals(1, saves.get(), "the originally queued callback must become a no-op after draining");
    }

    private ActiveIslandRegistry.ActiveIsland activeIsland() {
        return new ActiveIslandRegistry.ActiveIsland(
            UUID.randomUUID(), "ci_shard_001", 0, 0, 0, 0, 300, 1L, 7L, Instant.now()
        );
    }

    private PlatformScheduler scheduler(AtomicReference<Runnable> scheduled, CountDownLatch requestScheduled) {
        return new PlatformScheduler() {
            @Override public TaskHandle runGlobal(Runnable task) { scheduled.set(task); requestScheduled.countDown(); return TaskHandle.noop(); }
            @Override public TaskHandle runAsync(Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle runForPlayer(UUID playerId, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle runForChunk(String worldKey, int chunkX, int chunkZ, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle repeatGlobal(Duration delay, Duration interval, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public TaskHandle repeatAsync(Duration delay, Duration interval, Runnable task) { throw new UnsupportedOperationException(); }
            @Override public void close() { }
        };
    }
}
