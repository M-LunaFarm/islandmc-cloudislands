package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.platform.scheduler.PlatformScheduler;
import kr.lunaf.cloudislands.paper.platform.scheduler.TaskHandle;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PaperIslandJobWorkerShutdownTest {
    @TempDir
    Path tempDir;

    @Test
    void shutdownStopsNewPollsDrainsGlobalWorkAndWaitsForClaimToReturn() throws Exception {
        CapturingScheduler scheduler = new CapturingScheduler();
        CountDownLatch claimEntered = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        AtomicInteger claims = new AtomicInteger();
        AtomicInteger drains = new AtomicInteger();
        PaperIslandJobWorker worker = worker(scheduler, new EmptyJobSource() {
            @Override
            public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
                claims.incrementAndGet();
                claimEntered.countDown();
                await(releaseClaim);
                return List.of();
            }
        }, drains::incrementAndGet);

        worker.start(1L);
        Thread poll = Thread.startVirtualThread(scheduler.repeatingTask());
        assertTrue(claimEntered.await(2L, TimeUnit.SECONDS));

        AtomicBoolean shutdownResult = new AtomicBoolean();
        Thread shutdown = Thread.startVirtualThread(() -> shutdownResult.set(worker.shutdown(Duration.ofSeconds(2L))));
        awaitAtLeastOneDrain(drains);
        assertTrue(shutdown.isAlive(), "shutdown must not return while a claimed poll can still mutate island state");

        Thread rejectedPoll = Thread.startVirtualThread(scheduler.repeatingTask());
        rejectedPoll.join(1_000L);
        assertFalse(rejectedPoll.isAlive());
        assertEquals(1, claims.get(), "stop must reject scheduler callbacks that race with shutdown");

        releaseClaim.countDown();
        poll.join(2_000L);
        shutdown.join(2_000L);
        assertFalse(poll.isAlive());
        assertFalse(shutdown.isAlive());
        assertTrue(shutdownResult.get());
        assertTrue(drains.get() >= 1);
    }

    @Test
    void shutdownDeadlineFailsClosedWhileCoreLeaseKeepsClaimRecoverable() throws Exception {
        CapturingScheduler scheduler = new CapturingScheduler();
        CountDownLatch claimEntered = new CountDownLatch(1);
        CountDownLatch releaseClaim = new CountDownLatch(1);
        PaperIslandJobWorker worker = worker(scheduler, new EmptyJobSource() {
            @Override
            public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
                claimEntered.countDown();
                await(releaseClaim);
                return List.of();
            }
        }, () -> {});

        worker.start(1L);
        Thread poll = Thread.startVirtualThread(scheduler.repeatingTask());
        assertTrue(claimEntered.await(2L, TimeUnit.SECONDS));
        assertFalse(worker.shutdown(Duration.ofMillis(30L)));
        releaseClaim.countDown();
        poll.join(2_000L);
        assertFalse(poll.isAlive());
    }

    private PaperIslandJobWorker worker(
        CapturingScheduler scheduler,
        PaperIslandJobWorker.LocalJobSource source,
        PaperIslandJobWorker.ShutdownDrainer drainer
    ) {
        return new PaperIslandJobWorker(
            plugin(tempDir),
            source,
            null,
            null,
            new ActiveIslandRegistry(),
            null,
            "paper-a",
            scheduler,
            drainer
        );
    }

    private static Plugin plugin(Path dataFolder) {
        Logger logger = Logger.getLogger(PaperIslandJobWorkerShutdownTest.class.getName());
        return (Plugin) Proxy.newProxyInstance(Plugin.class.getClassLoader(), new Class<?>[] {Plugin.class}, (_proxy, method, _args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder.toFile();
            case "getLogger" -> logger;
            case "getName" -> "CloudIslands";
            case "isEnabled" -> true;
            default -> defaultValue(method.getReturnType());
        });
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
        return 0;
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static void awaitAtLeastOneDrain(AtomicInteger drains) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (drains.get() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(1L);
        }
        assertTrue(drains.get() > 0, "shutdown must service pending global-thread work while it waits");
    }

    private static class EmptyJobSource implements PaperIslandJobWorker.LocalJobSource {
        @Override
        public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
            return List.of();
        }

        @Override
        public void complete(String nodeId, UUID jobId) {
        }

        @Override
        public void complete(String nodeId, UUID jobId, Map<String, String> payload) {
        }

        @Override
        public void fail(String nodeId, UUID jobId, String errorMessage) {
        }
    }

    private static final class CapturingScheduler implements PlatformScheduler {
        private final AtomicReference<Runnable> repeating = new AtomicReference<>();

        Runnable repeatingTask() {
            return repeating.get();
        }

        @Override public TaskHandle runGlobal(Runnable task) { task.run(); return TaskHandle.noop(); }
        @Override public TaskHandle runAsync(Runnable task) { task.run(); return TaskHandle.noop(); }
        @Override public TaskHandle runForPlayer(UUID playerId, Runnable task) { task.run(); return TaskHandle.noop(); }
        @Override public TaskHandle runForChunk(String worldKey, int chunkX, int chunkZ, Runnable task) { task.run(); return TaskHandle.noop(); }
        @Override public TaskHandle repeatGlobal(Duration delay, Duration interval, Runnable task) { repeating.set(task); return TaskHandle.noop(); }
        @Override public TaskHandle repeatAsync(Duration delay, Duration interval, Runnable task) { repeating.set(task); return TaskHandle.noop(); }
        @Override public void close() { }
    }
}
