package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class JdkBlockValueQueryClient implements BlockValueQueryClient {
    private final JdkCoreApiClient core;

    JdkBlockValueQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<BlockValueView>> list() {
        return core.postResultBody("/v1/admin/block-values/list", "{}")
            .thenApply(CoreBlockValueJson::values);
    }
}
