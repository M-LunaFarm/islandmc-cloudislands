package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreJobCommandClient implements JobCommandClient {
    private final CoreApiClient delegate;

    public CoreJobCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<JobActionView> retry(UUID jobId) {
        requireId(jobId);
        return delegate.retryJobResult(jobId).thenApply(body -> CoreJobJson.action(body, "JOB_RETRIED"));
    }

    @Override
    public CompletableFuture<JobActionView> cancel(UUID jobId) {
        requireId(jobId);
        return delegate.cancelJobResult(jobId).thenApply(body -> CoreJobJson.action(body, "JOB_CANCELED"));
    }

    @Override
    public CompletableFuture<JobRecoveryView> recover(String nodeId, long minIdleMillis, int maxJobs) {
        String safeNodeId = nodeId == null ? "" : nodeId.trim();
        if (safeNodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return delegate.recoverJobsResult(safeNodeId, Math.max(0L, minIdleMillis), Math.max(1, maxJobs))
            .thenApply(CoreJobJson::recovery);
    }

    private static void requireId(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required");
        }
    }
}
