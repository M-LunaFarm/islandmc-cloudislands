package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ShutdownSaveCoordinatorTest {
    @Test
    void waitsForPeriodicSaveBeforeRunningFinalFlush() throws Exception {
        AtomicBoolean busy = new AtomicBoolean(true);
        AtomicBoolean flushed = new AtomicBoolean();
        Object monitor = new Object();
        Thread periodicSave = Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            busy.set(false);
            synchronized (monitor) {
                monitor.notifyAll();
            }
        });

        assertTrue(ShutdownSaveCoordinator.awaitIdleAndFlush(
            busy::get, monitor, () -> flushed.set(true), Duration.ofSeconds(1)
        ));
        assertTrue(flushed.get());
        periodicSave.join();
    }

    @Test
    void timesOutInsteadOfBlockingPaperShutdownForever() {
        AtomicBoolean busy = new AtomicBoolean(true);
        AtomicBoolean flushed = new AtomicBoolean();

        assertFalse(ShutdownSaveCoordinator.awaitIdleAndFlush(
            busy::get, new Object(), () -> flushed.set(true), Duration.ofMillis(20)
        ));
        assertFalse(flushed.get());
    }

    @Test
    void boundsAStalledFinalFlush() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);

        assertFalse(ShutdownSaveCoordinator.awaitIdleAndFlush(
            () -> false,
            new Object(),
            () -> {
                started.countDown();
                try {
                    release.await(1L, TimeUnit.SECONDS);
                } catch (InterruptedException caught) {
                    cancelled.countDown();
                    Thread.currentThread().interrupt();
                }
            },
            Duration.ofMillis(20)
        ));
        assertTrue(started.await(1L, TimeUnit.SECONDS));
        assertTrue(cancelled.await(1L, TimeUnit.SECONDS));
        release.countDown();
    }
}
