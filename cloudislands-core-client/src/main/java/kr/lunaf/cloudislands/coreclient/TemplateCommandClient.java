package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface TemplateCommandClient {
    CompletableFuture<TemplateView> upsert(String templateId, String displayName, boolean enabled, String minNodeVersion);

    CompletableFuture<TemplateView> upsert(TemplateView template);

    CompletableFuture<TemplateView> importBundle(TemplateView template);

    CompletableFuture<TemplateBundleVerificationView> verifyBundle(String templateId);

    CompletableFuture<TemplateView> enable(String templateId);

    CompletableFuture<TemplateView> disable(String templateId);

    CompletableFuture<Boolean> delete(String templateId, boolean confirm);

    CompletableFuture<TemplateView> reorder(String templateId, int sortOrder);
}
