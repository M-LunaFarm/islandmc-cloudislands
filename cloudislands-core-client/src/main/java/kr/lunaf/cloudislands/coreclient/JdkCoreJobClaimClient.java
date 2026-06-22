package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.json.IslandJobJson;

final class JdkCoreJobClaimClient implements JobClaimClient {
    private final JdkCoreApiClient core;

    JdkCoreJobClaimClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        String types = supportedTypes.stream().map(Enum::name).collect(Collectors.joining(","));
        return core.postResultBody("/v1/jobs/claim", CoreJsonPayload.object("nodeId", nodeId, "supportedTypes", types, "maxJobs", maxJobs))
            .thenApply(CoreResponseBody::value)
            .thenApply(IslandJobJson::readArray);
    }
}
