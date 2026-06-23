package kr.lunaf.cloudislands.coreservice.job;

import java.util.UUID;

public interface JobCompletionReceiptStore {
    enum RecordResult {
        NEW,
        REPLAY,
        CONFLICT
    }

    RecordResult record(JobCompletionRequest request);

    void forget(UUID jobId, String requestHash);
}
