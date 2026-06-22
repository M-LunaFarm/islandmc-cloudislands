package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class JdkJobClient implements JobQueryClient {
    private final JdkCoreApiClient core;

    JdkJobClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<JobView>> list() {
        return core.postWithResultBody("/v1/admin/jobs/list", "{}")
            .thenApply(CoreJobJson::jobs);
    }
}
