package kr.lunaf.cloudislands.coreservice.job;

import java.util.UUID;
import java.util.Map;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;

public interface JobCompletionReceiptStore {
    enum RecordResult {
        NEW,
        REPLAY,
        CONFLICT
    }

    enum ReplayResult {
        MATCH,
        CONFLICT,
        MISSING
    }

    record RecordOutcome(RecordResult result, long aggregateVersion) {
    }

    RecordOutcome record(JobCompletionRequest request);

    ReplayResult verifyCommitted(UUID jobId, String nodeId, JobClaimLease claimLease, Map<String, String> completionPayload);

    void forget(UUID jobId, String requestHash);
}
