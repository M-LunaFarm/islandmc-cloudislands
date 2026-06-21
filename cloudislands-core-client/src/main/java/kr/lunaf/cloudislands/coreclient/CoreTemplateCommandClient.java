package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public final class CoreTemplateCommandClient implements TemplateCommandClient {
    private final CoreApiClient delegate;

    public CoreTemplateCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<TemplateView> upsert(String templateId, String displayName, boolean enabled, String minNodeVersion) {
        return delegate.upsertTemplate(requireTemplateId(templateId), displayName == null ? "" : displayName, enabled, minNodeVersion == null ? "" : minNodeVersion)
            .thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> enable(String templateId) {
        return delegate.enableTemplate(requireTemplateId(templateId)).thenApply(CoreTemplateJson::template);
    }

    @Override
    public CompletableFuture<TemplateView> disable(String templateId) {
        return delegate.disableTemplate(requireTemplateId(templateId)).thenApply(CoreTemplateJson::template);
    }

    private static String requireTemplateId(String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("templateId is required");
        }
        return templateId.trim();
    }
}
