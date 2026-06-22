package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkJobCommandClient implements JobCommandClient {
    private final JdkCoreApiClient core;

    JdkJobCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<JobActionView> retry(UUID jobId) {
        requireId(jobId, "jobId");
        return core.postWithResultBody("/v1/admin/jobs/retry", JdkCoreApiClient.jsonObject("jobId", jobId))
            .thenApply(body -> CoreJobJson.action(body, "JOB_RETRIED"));
    }

    @Override
    public CompletableFuture<JobActionView> cancel(UUID jobId) {
        requireId(jobId, "jobId");
        return core.postWithResultBody("/v1/admin/jobs/cancel", JdkCoreApiClient.jsonObject("jobId", jobId))
            .thenApply(body -> CoreJobJson.action(body, "JOB_CANCELED"));
    }

    @Override
    public CompletableFuture<JobRecoveryView> recover(String nodeId, long minIdleMillis, int maxJobs) {
        return core.postWithResultBody("/v1/admin/jobs/recover", JdkCoreApiClient.jsonObject("nodeId", requireJobNode(nodeId), "minIdleMillis", Math.max(0L, minIdleMillis), "maxJobs", Math.max(1, maxJobs)))
            .thenApply(CoreJobJson::recovery);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String requireJobNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return nodeId.trim();
    }
}
