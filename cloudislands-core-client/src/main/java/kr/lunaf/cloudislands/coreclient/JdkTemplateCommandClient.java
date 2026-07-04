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
        return core.postResultBody(
                "/v1/admin/templates/upsert",
                CoreJsonPayload.object("templateId", requireTemplateId(templateId), "displayName", displayName == null ? "" : displayName, "enabled", enabled, "minNodeVersion", minNodeVersion == null ? "" : minNodeVersion)
            )
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> upsert(TemplateView template) {
        return core.postResultBody("/v1/admin/templates/upsert", templatePayload(template))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> importBundle(TemplateView template) {
        return core.postResultBody("/v1/admin/templates/import-bundle", templatePayload(template))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateBundleVerificationView> verifyBundle(String templateId) {
        return core.postResultBody("/v1/admin/templates/verify-bundle", CoreJsonPayload.object("templateId", requireTemplateId(templateId)))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::bundleVerification);
    }

    @Override
    public CompletableFuture<TemplateView> enable(String templateId) {
        return core.postResultBody("/v1/admin/templates/enable", CoreJsonPayload.object("templateId", requireTemplateId(templateId)))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> disable(String templateId) {
        return core.postResultBody("/v1/admin/templates/disable", CoreJsonPayload.object("templateId", requireTemplateId(templateId)))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<Boolean> delete(String templateId, boolean confirm) {
        return core.postResultBody("/v1/admin/templates/delete", CoreJsonPayload.object("templateId", requireTemplateId(templateId), "confirm", confirm))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::accepted);
    }

    @Override
    public CompletableFuture<TemplateView> reorder(String templateId, int sortOrder) {
        return core.postResultBody("/v1/admin/templates/reorder", CoreJsonPayload.object("templateId", requireTemplateId(templateId), "sortOrder", Math.max(0, sortOrder)))
            .thenApply(CoreResponseBody::value)
            .thenApply(CoreTemplateJson::template);
    }

    private static String templatePayload(TemplateView template) {
        TemplateView safe = template == null ? new TemplateView("default", "Default Island", true, "") : template;
        return CoreJsonPayload.object(
            "templateId", requireTemplateId(safe.id()),
            "displayName", safe.displayName(),
            "description", safe.description(),
            "category", safe.category(),
            "enabled", safe.enabled(),
            "minNodeVersion", safe.minNodeVersion(),
            "requiredPermission", safe.requiredPermission(),
            "iconMaterial", safe.iconMaterial(),
            "iconCustomModelData", safe.iconCustomModelData(),
            "previewImageKey", safe.previewImageKey(),
            "bundleStoragePath", safe.bundleStoragePath(),
            "bundleChecksum", safe.bundleChecksum(),
            "bundleSizeBytes", safe.bundleSizeBytes(),
            "schemaVersion", safe.schemaVersion(),
            "defaultIslandSize", safe.defaultIslandSize(),
            "spawnWorldOffsetX", safe.spawnWorldOffsetX(),
            "spawnWorldOffsetY", safe.spawnWorldOffsetY(),
            "spawnWorldOffsetZ", safe.spawnWorldOffsetZ(),
            "spawnYaw", safe.spawnYaw(),
            "spawnPitch", safe.spawnPitch(),
            "homeName", safe.homeName(),
            "environmentPreset", safe.environmentPreset(),
            "biomeKey", safe.biomeKey(),
            "borderColor", safe.borderColor(),
            "bankInitialBalance", safe.bankInitialBalance(),
            "creationCost", safe.creationCost(),
            "sortOrder", safe.sortOrder(),
            "tags", String.join(",", safe.tags())
        );
    }

    private static String requireTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        return templateId.trim();
    }
}
