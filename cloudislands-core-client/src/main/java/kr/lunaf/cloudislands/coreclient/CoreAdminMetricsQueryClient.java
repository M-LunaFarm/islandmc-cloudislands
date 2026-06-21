package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public final class CoreAdminMetricsQueryClient implements AdminMetricsQueryClient {
    private final CoreApiClient delegate;

    public CoreAdminMetricsQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminMetricsSummaryView> summary() {
        return delegate.metrics().thenApply(AdminMetricsSummaryView::parse);
    }
}
