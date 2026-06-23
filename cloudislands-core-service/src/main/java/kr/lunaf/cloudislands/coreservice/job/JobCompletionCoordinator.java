package kr.lunaf.cloudislands.coreservice.job;

import java.util.Map;
import kr.lunaf.cloudislands.protocol.job.IslandJob;

final class JobCompletionCoordinator {
    private final JobCompletionBackend backend;
    private final JobCompletionReceiptStore receipts;

    JobCompletionCoordinator(JobCompletionBackend backend, JobCompletionReceiptStore receipts) {
        this.backend = backend;
        this.receipts = receipts;
    }

    JobCompletionResult completed(IslandJob claimedJob, Map<String, String> completionPayload) {
        JobCompletionRequest request = JobCompletionRequest.completed(claimedJob, completionPayload);
        JobCompletionReceiptStore.RecordResult recordResult = receipts.record(request);
        if (recordResult == JobCompletionReceiptStore.RecordResult.CONFLICT) {
            throw new JobCompletionConflictException("job completion request hash differs from the committed receipt");
        }
        if (recordResult == JobCompletionReceiptStore.RecordResult.REPLAY) {
            return new JobCompletionResult(true, request.requestHash());
        }
        try {
            backend.completed(request.job());
            return new JobCompletionResult(false, request.requestHash());
        } catch (RuntimeException exception) {
            receipts.forget(request.job().jobId(), request.requestHash());
            throw exception;
        }
    }
}
