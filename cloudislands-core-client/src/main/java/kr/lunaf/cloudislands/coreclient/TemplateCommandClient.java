package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface TemplateCommandClient {
    CompletableFuture<TemplateView> upsert(String templateId, String displayName, boolean enabled, String minNodeVersion);

    CompletableFuture<TemplateView> enable(String templateId);

    CompletableFuture<TemplateView> disable(String templateId);
}
