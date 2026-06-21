package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreBlockValueQueryClient implements BlockValueQueryClient {
    private final CoreApiClient delegate;

    public CoreBlockValueQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<BlockValueView>> list() {
        return delegate.listBlockValues().thenApply(CoreBlockValueJson::values);
    }
}
