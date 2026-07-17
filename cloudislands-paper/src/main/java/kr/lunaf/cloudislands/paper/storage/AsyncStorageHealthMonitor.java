package kr.lunaf.cloudislands.paper.storage;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.paper.bootstrap.RuntimeComponent;
import kr.lunaf.cloudislands.storage.IslandStorage;

public final class AsyncStorageHealthMonitor implements RuntimeComponent {
    private static final long FAILURE_LOG_INTERVAL_MILLIS = 30_000L;

    private final StorageProbe probe;
    private final Logger logger;
    private final long intervalMillis;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean available;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private volatile boolean probeCompleted;
    private volatile long lastFailureLogMillis;

    AsyncStorageHealthMonitor(StorageProbe probe, Logger logger, Duration interval, boolean initiallyAvailable) {
        this.probe = probe;
        this.logger = logger;
        this.intervalMillis = Math.max(100L, Objects.requireNonNull(interval, "interval").toMillis());
        this.available = new AtomicBoolean(initiallyAvailable);
        this.executor = probe == null ? null : Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CloudIslands-StorageHealth");
            thread.setDaemon(true);
            return thread;
        });
    }

    public static AsyncStorageHealthMonitor start(IslandStorage storage, Logger logger, Duration interval) {
        AsyncStorageHealthMonitor monitor = storage == null
            ? new AsyncStorageHealthMonitor(null, logger, interval, true)
            : new AsyncStorageHealthMonitor(storage::available, logger, interval, false);
        monitor.start();
        return monitor;
    }

    void start() {
        if (executor == null || !started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleWithFixedDelay(this::probe, 0L, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public boolean available() {
        return available.get();
    }

    @Override
    public void stop() {
        if (!stopped.compareAndSet(false, true) || executor == null) {
            return;
        }
        executor.shutdownNow();
    }

    private void probe() {
        if (stopped.get()) {
            return;
        }
        try {
            boolean next = probe.available();
            boolean previous = available.getAndSet(next);
            boolean completedBefore = probeCompleted;
            probeCompleted = true;
            if (!next) {
                logUnavailable(completedBefore && !previous, "Island storage health check returned unavailable");
            } else if (completedBefore && !previous && logger != null) {
                logger.info("Island storage health check recovered");
            }
        } catch (Exception exception) {
            boolean previous = available.getAndSet(false);
            boolean completedBefore = probeCompleted;
            probeCompleted = true;
            logUnavailable(completedBefore && !previous, "Island storage health check failed: " + exception.getMessage());
        }
    }

    private void logUnavailable(boolean alreadyUnavailable, String message) {
        if (logger == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (alreadyUnavailable && now - lastFailureLogMillis < FAILURE_LOG_INTERVAL_MILLIS) {
            return;
        }
        lastFailureLogMillis = now;
        logger.warning(message);
    }

    @FunctionalInterface
    interface StorageProbe {
        boolean available() throws Exception;
    }
}
