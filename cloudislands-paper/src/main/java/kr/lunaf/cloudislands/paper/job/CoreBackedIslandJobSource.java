package kr.lunaf.cloudislands.paper.job;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class CoreBackedIslandJobSource implements PaperIslandJobWorker.LocalJobSource {
    private final CoreApiClient coreApiClient;

    public CoreBackedIslandJobSource(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
    }

    @Override
    public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        return coreApiClient.claimJobs(nodeId, supportedTypes, maxJobs).join();
    }

    @Override
    public void complete(String nodeId, UUID jobId) {
        coreApiClient.completeJob(nodeId, jobId);
    }

    @Override
    public void fail(String nodeId, UUID jobId, String errorMessage) {
        coreApiClient.failJob(nodeId, jobId, errorMessage);
    }
}
