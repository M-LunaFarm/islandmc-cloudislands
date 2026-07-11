package kr.lunaf.cloudislands.paper.activation;

import java.time.Duration;
import java.util.function.BooleanSupplier;

final class ShutdownSaveCoordinator {
    private ShutdownSaveCoordinator() {
    }

    static boolean awaitIdleAndFlush(BooleanSupplier busy, Object monitor, Runnable flush, Duration timeout) {
        long timeoutMillis = Math.max(1L, timeout == null ? 30_000L : timeout.toMillis());
        long deadlineNanos = System.nanoTime() + timeoutMillis * 1_000_000L;
        if (!awaitIdle(busy, monitor, deadlineNanos)) {
            return false;
        }
        Thread flushThread = Thread.ofVirtual().name("cloudislands-shutdown-save").start(flush);
        return join(flushThread, deadlineNanos);
    }

    private static boolean awaitIdle(BooleanSupplier busy, Object monitor, long deadlineNanos) {
        synchronized (monitor) {
            while (busy.getAsBoolean()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return false;
                }
                try {
                    long millis = Math.max(1L, remainingNanos / 1_000_000L);
                    monitor.wait(millis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean join(Thread thread, long deadlineNanos) {
        while (thread.isAlive()) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return false;
            }
            try {
                thread.join(Math.max(1L, remainingNanos / 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }
}
