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
        return core.postResultBody("/v1/admin/templates/list", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::templates);
    }

    @Override
    public CompletableFuture<TemplateView> get(String templateId) {
        return core.postResultBody("/v1/admin/templates/get", CoreJsonPayload.object("templateId", requireTemplateId(templateId)))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    private static String requireTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        return templateId.trim();
    }
}
