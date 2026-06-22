package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

final class JdkAdminCoreConfigClient implements AdminCoreConfigQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminCoreConfigClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminCoreConfigView> config() {
        return core.postResultBody("/v1/admin/config", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(AdminCoreConfigView::parse);
    }
}
