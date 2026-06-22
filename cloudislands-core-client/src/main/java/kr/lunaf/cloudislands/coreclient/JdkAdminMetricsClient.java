package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

final class JdkAdminMetricsClient implements AdminMetricsQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminMetricsClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminMetricsSummaryView> summary() {
        return core.postWithResultBody("/metrics", "{}")
            .thenApply(AdminMetricsSummaryView::parse);
    }
}
