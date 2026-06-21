package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface JobCommandClient {
    CompletableFuture<JobActionView> retry(UUID jobId);

    CompletableFuture<JobActionView> cancel(UUID jobId);

    CompletableFuture<JobRecoveryView> recover(String nodeId, long minIdleMillis, int maxJobs);
}
