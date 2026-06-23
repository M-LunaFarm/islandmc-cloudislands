package kr.lunaf.cloudislands.coreservice.job;

import java.util.UUID;

public interface JobCompletionReceiptStore {
    enum RecordResult {
        NEW,
        REPLAY,
        CONFLICT
    }

    record RecordOutcome(RecordResult result, long aggregateVersion) {
    }

    RecordOutcome record(JobCompletionRequest request);

    void forget(UUID jobId, String requestHash);
}
