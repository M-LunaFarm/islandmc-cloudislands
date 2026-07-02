package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

final class JdkAdminSupportBundleClient implements AdminSupportBundleClient {
    private final JdkCoreApiClient core;

    JdkAdminSupportBundleClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<String> create() {
        return core.postResultBody("/v1/admin/support-bundle", "{}")
            .thenApply(CoreResponseBody::value);
    }
}
