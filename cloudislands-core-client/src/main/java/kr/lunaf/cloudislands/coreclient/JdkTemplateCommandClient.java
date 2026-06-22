package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

final class JdkTemplateCommandClient implements TemplateCommandClient {
    private final JdkCoreApiClient core;

    JdkTemplateCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<TemplateView> upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        return core.postWithResultBody(
                "/v1/admin/templates/upsert",
                JdkCoreApiClient.jsonObject("templateId", requireTemplateId(templateId), "displayName", displayName == null ? "" : displayName, "enabled", enabled, "minNodeVersion", minNodeVersion == null ? "" : minNodeVersion)
            )
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> enable(String templateId) {
        return core.postWithResultBody("/v1/admin/templates/enable", JdkCoreApiClient.jsonObject("templateId", requireTemplateId(templateId)))
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> disable(String templateId) {
        return core.postWithResultBody("/v1/admin/templates/disable", JdkCoreApiClient.jsonObject("templateId", requireTemplateId(templateId)))
            .thenApply(CoreTemplateJson::template);
    }

    private static String requireTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        return templateId.trim();
    }
}
