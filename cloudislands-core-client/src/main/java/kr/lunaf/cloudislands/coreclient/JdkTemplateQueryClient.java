package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

final class JdkTemplateQueryClient implements TemplateQueryClient {
    private final JdkCoreApiClient core;

    JdkTemplateQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<TemplateView>> list() {
        return core.postWithResultBody("/v1/admin/templates/list", "{}")
            .thenApply(CoreTemplateJson::templates);
    }
}
