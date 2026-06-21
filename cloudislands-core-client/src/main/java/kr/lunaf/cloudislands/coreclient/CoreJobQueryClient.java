package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreJobQueryClient implements JobQueryClient {
    private final CoreApiClient delegate;

    public CoreJobQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<JobView>> list() {
        return delegate.listJobs().thenApply(CoreJobJson::jobs);
    }
}
