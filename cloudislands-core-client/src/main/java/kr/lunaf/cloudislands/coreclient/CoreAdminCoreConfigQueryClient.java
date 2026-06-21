package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public final class CoreAdminCoreConfigQueryClient implements AdminCoreConfigQueryClient {
    private final CoreApiClient delegate;

    public CoreAdminCoreConfigQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminCoreConfigView> config() {
        return delegate.coreConfig().thenApply(AdminCoreConfigView::parse);
    }
}
