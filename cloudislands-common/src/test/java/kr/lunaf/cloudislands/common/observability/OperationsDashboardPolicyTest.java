package kr.lunaf.cloudislands.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OperationsDashboardPolicyTest {
    @Test
    void listsEveryRequiredOperationsDashboardPanel() {
        assertEquals(
            Map.ofEntries(
                Map.entry("node-players-by-node", "cloudislands_node_players"),
                Map.entry("node-player-usage-by-node", "cloudislands_node_player_usage_ratio"),
                Map.entry("node-player-usage-cluster", "cloudislands_cluster_player_usage_ratio"),
                Map.entry("node-mspt-by-node", "cloudislands_node_mspt"),
                Map.entry("node-mspt-over-budget", "cloudislands_node_mspt_over_budget"),
                Map.entry("active-islands", "cloudislands_node_active_islands"),
                Map.entry("active-island-usage-by-node", "cloudislands_node_active_island_usage_ratio"),
                Map.entry("active-island-usage-cluster", "cloudislands_cluster_active_island_usage_ratio"),
                Map.entry("average-island-activation-seconds", "cloudislands_cluster_avg_island_activation_seconds"),
                Map.entry("island-activation-seconds-by-node", "cloudislands_island_activation_seconds"),
                Map.entry("island-save-duration", "cloudislands_island_save_seconds"),
                Map.entry("island-snapshot-duration", "cloudislands_island_snapshot_seconds"),
                Map.entry("island-save-failures", "cloudislands_island_save_failures_total"),
                Map.entry("route-ticket-created", "cloudislands_route_ticket_created_total"),
                Map.entry("route-ticket-consumed", "cloudislands_route_ticket_consumed_total"),
                Map.entry("route-failures", "cloudislands_route_ticket_failed_total"),
                Map.entry("job-queue-depth", "cloudislands_jobs_pending"),
                Map.entry("job-retry-count", "cloudislands_jobs_retry_total"),
                Map.entry("node-heartbeat-age", "cloudislands_node_heartbeat_age_seconds"),
                Map.entry("ranking-cache-stale", "cloudislands_ranking_cache_stale"),
                Map.entry("ranking-dirty-pending", "cloudislands_ranking_dirty_pending"),
                Map.entry("ranking-dirty-drained", "cloudislands_ranking_dirty_drained_total"),
                Map.entry("ranking-recalculation-failures", "cloudislands_ranking_recalculation_failures_total"),
                Map.entry("ranking-recalculation-last-batch-size", "cloudislands_ranking_recalculation_last_batch_size"),
                Map.entry("redis-latency", "cloudislands_redis_latency_seconds"),
                Map.entry("database-query-latency", "cloudislands_database_query_seconds"),
                Map.entry("database-connection-pool-usage", "cloudislands_database_connection_pool_usage_ratio"),
                Map.entry("database-connection-pool-available", "cloudislands_database_connections_available"),
                Map.entry("database-connection-pool-saturated", "cloudislands_database_connection_pool_saturated"),
                Map.entry("storage-upload-duration", "cloudislands_storage_upload_seconds"),
                Map.entry("storage-download-duration", "cloudislands_storage_download_seconds"),
                Map.entry("object-storage-failure-ratio", "cloudislands_cluster_storage_failure_ratio"),
                Map.entry("object-storage-node-failure-ratio", "cloudislands_storage_failure_ratio"),
                Map.entry("permission-cache-hit-ratio", "cloudislands_permission_cache_hit_ratio"),
                Map.entry("core-api-error-rate", "cloudislands_core_security_rejects_total")
            ),
            OperationsDashboardPolicy.requiredDashboardMetrics()
        );
    }

    @Test
    void resolvesBackingMetricsByStablePanelKey() {
        assertTrue(OperationsDashboardPolicy.requiredDashboardPanel("route-failures"));
        assertEquals(
            "cloudislands_route_ticket_failed_total",
            OperationsDashboardPolicy.backingMetric("route-failures")
        );
        assertFalse(OperationsDashboardPolicy.requiredDashboardPanel("vanity-chart"));
        assertEquals("", OperationsDashboardPolicy.backingMetric("vanity-chart"));
        assertEquals("", OperationsDashboardPolicy.backingMetric(null));
    }
}
