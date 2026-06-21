package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;

public interface AdminMetricsQueryClient {
    CompletableFuture<AdminMetricsSummaryView> summary();
}
