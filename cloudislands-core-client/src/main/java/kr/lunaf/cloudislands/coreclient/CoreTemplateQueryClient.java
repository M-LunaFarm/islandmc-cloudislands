package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreTemplateQueryClient implements TemplateQueryClient {
    private final CoreApiClient delegate;

    public CoreTemplateQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<TemplateView>> list() {
        return delegate.listTemplates().thenApply(CoreTemplateJson::templates);
    }
}
