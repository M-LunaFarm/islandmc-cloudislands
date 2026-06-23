package kr.lunaf.cloudislands.coreservice.job;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryJobCompletionOutboxStore implements JobCompletionOutboxStore {
    private static final Duration CLAIM_LEASE = Duration.ofSeconds(30);

    private final Map<UUID, EntryState> entries = new LinkedHashMap<>();

    @Override
    public synchronized void append(List<JobCompletionEvent> events) {
        for (JobCompletionEvent event : events == null ? List.<JobCompletionEvent>of() : events) {
            entries.putIfAbsent(event.eventId(), new EntryState(event, "PENDING", 0, Instant.EPOCH));
        }
    }

    @Override
    public synchronized List<JobCompletionOutboxEntry> claimDue(int limit, Instant now) {
        List<JobCompletionOutboxEntry> due = new ArrayList<>();
        Instant safeNow = now == null ? Instant.now() : now;
        for (EntryState state : entries.values()) {
            if (due.size() >= Math.max(1, limit)) {
                break;
            }
            if ((!state.status.equals("PENDING") && !state.status.equals("DISPATCHING")) || state.nextAttemptAt.isAfter(safeNow)) {
                continue;
            }
            state.status = "DISPATCHING";
            state.attempts++;
            state.nextAttemptAt = safeNow.plus(CLAIM_LEASE);
            due.add(state.toEntry());
        }
        return List.copyOf(due);
    }

    @Override
    public synchronized void markDispatched(UUID eventId) {
        EntryState state = entries.get(eventId);
        if (state != null) {
            state.status = "DISPATCHED";
        }
    }

    @Override
    public synchronized void markFailed(UUID eventId, String error, Instant nextAttemptAt, boolean terminal) {
        EntryState state = entries.get(eventId);
        if (state != null) {
            state.status = terminal ? "FAILED" : "PENDING";
            state.nextAttemptAt = nextAttemptAt == null ? Instant.now() : nextAttemptAt;
        }
    }

    @Override
    public synchronized long pendingCount() {
        return entries.values().stream().filter(entry -> entry.status.equals("PENDING") || entry.status.equals("DISPATCHING")).count();
    }

    private static final class EntryState {
        private final JobCompletionEvent event;
        private String status;
        private int attempts;
        private Instant nextAttemptAt;

        private EntryState(JobCompletionEvent event, String status, int attempts, Instant nextAttemptAt) {
            this.event = event;
            this.status = status;
            this.attempts = attempts;
            this.nextAttemptAt = nextAttemptAt;
        }

        private JobCompletionOutboxEntry toEntry() {
            return new JobCompletionOutboxEntry(event.eventId(), event.aggregateId(), event.aggregateVersion(), event.eventType(), event.fields(), attempts);
        }
    }
}
