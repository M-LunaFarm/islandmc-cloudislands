package kr.lunaf.cloudislands.paper.storage;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class AsyncStorageHealthMonitorTest {
    @Test
    void availabilityReadNeverWaitsForBlockedStorageProbe() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncStorageHealthMonitor monitor = monitor(() -> {
            entered.countDown();
            release.await();
            return true;
        });

        try {
            monitor.start();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            long started = System.nanoTime();
            assertFalse(monitor.available(), "storage must fail closed until the first probe completes");
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 50L, "cached availability must not wait for object storage");
            release.countDown();
            awaitAvailable(monitor, true);
        } finally {
            release.countDown();
            monitor.stop();
        }
    }

    @Test
    void failedProbeRecoversWithoutBlockingCallers() throws Exception {
        AtomicBoolean storageAvailable = new AtomicBoolean(false);
        AsyncStorageHealthMonitor monitor = monitor(storageAvailable::get);

        try {
            monitor.start();
            Thread.sleep(150L);
            assertFalse(monitor.available());
            storageAvailable.set(true);
            awaitAvailable(monitor, true);
            storageAvailable.set(false);
            awaitAvailable(monitor, false);
        } finally {
            monitor.stop();
        }
    }

    private AsyncStorageHealthMonitor monitor(AsyncStorageHealthMonitor.StorageProbe probe) {
        return new AsyncStorageHealthMonitor(probe, null, Duration.ofMillis(25L), false);
    }

    private void awaitAvailable(AsyncStorageHealthMonitor monitor, boolean expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2L);
        while (monitor.available() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertTrue(monitor.available() == expected, "timed out waiting for storage health transition");
    }
}
