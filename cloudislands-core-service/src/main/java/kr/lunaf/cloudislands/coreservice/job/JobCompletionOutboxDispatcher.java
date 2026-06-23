package kr.lunaf.cloudislands.coreservice.job;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;

public final class JobCompletionOutboxDispatcher {
    private final JobCompletionOutboxStore store;
    private final GlobalEventPublisher publisher;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicLong dispatchedTotal = new AtomicLong();
    private final AtomicLong failedTotal = new AtomicLong();

    public JobCompletionOutboxDispatcher(JobCompletionOutboxStore store, GlobalEventPublisher publisher) {
        this(store, publisher, 32, 5, Duration.ofMillis(250), Duration.ofSeconds(1));
    }

    JobCompletionOutboxDispatcher(JobCompletionOutboxStore store, GlobalEventPublisher publisher, int batchSize, int maxAttempts, Duration baseBackoff, Duration interval) {
        this.store = store;
        this.publisher = publisher;
        this.batchSize = Math.max(1, Math.min(batchSize, 100));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseBackoff = baseBackoff == null || baseBackoff.isNegative() || baseBackoff.isZero() ? Duration.ofMillis(250) : baseBackoff;
        this.interval = interval == null || interval.isNegative() || interval.isZero() ? Duration.ofSeconds(1) : interval;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudislands-completion-outbox-dispatcher");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::dispatchDue, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public void stop() {
        dispatchDue();
        executor.shutdownNow();
    }

    public void dispatchDue() {
        java.util.List<JobCompletionOutboxEntry> entries;
        try {
            entries = store.claimDue(batchSize, Instant.now());
        } catch (RuntimeException exception) {
            failedTotal.incrementAndGet();
            return;
        }
        for (JobCompletionOutboxEntry entry : entries) {
            try {
                publisher.publish(entry.eventType(), dispatchFields(entry));
                store.markDispatched(entry.eventId());
                dispatchedTotal.incrementAndGet();
            } catch (RuntimeException exception) {
                boolean terminal = entry.attempts() >= maxAttempts;
                store.markFailed(entry.eventId(), exception.getMessage(), nextAttemptAt(entry), terminal);
                failedTotal.incrementAndGet();
            }
        }
    }

    public long dispatchedTotal() {
        return dispatchedTotal.get();
    }

    public long failedTotal() {
        return failedTotal.get();
    }

    public long pendingCount() {
        return store.pendingCount();
    }

    private Map<String, String> dispatchFields(JobCompletionOutboxEntry entry) {
        Map<String, String> fields = new LinkedHashMap<>(entry.fields());
        fields.put("eventId", entry.eventId().toString());
        fields.put("aggregateId", entry.aggregateId().toString());
        fields.put("aggregateVersion", Long.toString(entry.aggregateVersion()));
        return Map.copyOf(fields);
    }

    private Instant nextAttemptAt(JobCompletionOutboxEntry entry) {
        long multiplier = Math.min(32L, 1L << Math.min(entry.attempts(), 5));
        long jitterMillis = Math.floorMod(entry.eventId().hashCode(), 250);
        return Instant.now().plus(baseBackoff.multipliedBy(multiplier)).plusMillis(jitterMillis);
    }
}
