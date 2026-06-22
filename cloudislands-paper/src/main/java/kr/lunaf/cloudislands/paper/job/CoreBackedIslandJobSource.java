package kr.lunaf.cloudislands.paper.job;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RuntimeCommandClient;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class CoreBackedIslandJobSource implements PaperIslandJobWorker.LocalJobSource {
    private final CoreApiClient coreApiClient;
    private final RuntimeCommandClient runtimeCommands;

    public CoreBackedIslandJobSource(CoreApiClient coreApiClient) {
        this.coreApiClient = coreApiClient;
        this.runtimeCommands = coreApiClient.runtimeCommands();
    }

    @Override
    public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        return coreApiClient.claimJobs(nodeId, supportedTypes, maxJobs).join();
    }

    @Override
    public void complete(String nodeId, UUID jobId) {
        runtimeCommands.completeJob(nodeId, jobId, Map.of());
    }

    @Override
    public void complete(String nodeId, UUID jobId, Map<String, String> payload) {
        runtimeCommands.completeJob(nodeId, jobId, payload);
    }

    @Override
    public void fail(String nodeId, UUID jobId, String errorMessage) {
        runtimeCommands.failJob(nodeId, jobId, errorMessage);
    }
}
