package kr.lunaf.cloudislands.coreservice.job;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JobCompletionOutboxStore {
    void append(List<JobCompletionEvent> events);

    List<JobCompletionOutboxEntry> claimDue(int limit, Instant now);

    void markDispatched(UUID eventId);

    void markFailed(UUID eventId, String error, Instant nextAttemptAt, boolean terminal);

    long pendingCount();
}
