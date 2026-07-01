package kr.lunaf.cloudislands.common.observability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OperationsDashboardPolicy {
    private static final Map<String, String> REQUIRED_DASHBOARD_METRICS = requiredMetrics();

    private OperationsDashboardPolicy() {
    }

    public static Map<String, String> requiredDashboardMetrics() {
        return REQUIRED_DASHBOARD_METRICS;
    }

    public static boolean requiredDashboardPanel(String panelKey) {
        return panelKey != null && REQUIRED_DASHBOARD_METRICS.containsKey(panelKey);
    }

    public static String backingMetric(String panelKey) {
        return panelKey == null ? "" : REQUIRED_DASHBOARD_METRICS.getOrDefault(panelKey, "");
    }

    private static Map<String, String> requiredMetrics() {
        LinkedHashMap<String, String> metrics = new LinkedHashMap<>();
        metrics.put("node-players-by-node", "cloudislands_node_players");
        metrics.put("node-player-usage-by-node", "cloudislands_node_player_usage_ratio");
        metrics.put("node-player-usage-cluster", "cloudislands_cluster_player_usage_ratio");
        metrics.put("node-mspt-by-node", "cloudislands_node_mspt");
        metrics.put("node-mspt-over-budget", "cloudislands_node_mspt_over_budget");
        metrics.put("active-islands", "cloudislands_node_active_islands");
        metrics.put("active-island-usage-by-node", "cloudislands_node_active_island_usage_ratio");
        metrics.put("active-island-usage-cluster", "cloudislands_cluster_active_island_usage_ratio");
        metrics.put("average-island-activation-seconds", "cloudislands_cluster_avg_island_activation_seconds");
        metrics.put("island-activation-seconds-by-node", "cloudislands_island_activation_seconds");
        metrics.put("island-save-duration", "cloudislands_island_save_seconds");
        metrics.put("island-snapshot-duration", "cloudislands_island_snapshot_seconds");
        metrics.put("island-save-failures", "cloudislands_island_save_failures_total");
        metrics.put("route-ticket-created", "cloudislands_route_ticket_created_total");
        metrics.put("route-ticket-consumed", "cloudislands_route_ticket_consumed_total");
        metrics.put("route-failures", "cloudislands_route_ticket_failed_total");
        metrics.put("job-queue-depth", "cloudislands_jobs_pending");
        metrics.put("job-retry-count", "cloudislands_jobs_retry_total");
        metrics.put("node-heartbeat-age", "cloudislands_node_heartbeat_age_seconds");
        metrics.put("ranking-cache-stale", "cloudislands_ranking_cache_stale");
        metrics.put("ranking-dirty-pending", "cloudislands_ranking_dirty_pending");
        metrics.put("ranking-dirty-drained", "cloudislands_ranking_dirty_drained_total");
        metrics.put("ranking-recalculation-failures", "cloudislands_ranking_recalculation_failures_total");
        metrics.put("ranking-recalculation-last-batch-size", "cloudislands_ranking_recalculation_last_batch_size");
        metrics.put("redis-latency", "cloudislands_redis_latency_seconds");
        metrics.put("database-query-latency", "cloudislands_database_query_seconds");
        metrics.put("database-connection-pool-usage", "cloudislands_database_connection_pool_usage_ratio");
        metrics.put("database-connection-pool-available", "cloudislands_database_connections_available");
        metrics.put("database-connection-pool-saturated", "cloudislands_database_connection_pool_saturated");
        metrics.put("storage-upload-duration", "cloudislands_storage_upload_seconds");
        metrics.put("storage-download-duration", "cloudislands_storage_download_seconds");
        metrics.put("object-storage-failure-ratio", "cloudislands_cluster_storage_failure_ratio");
        metrics.put("object-storage-node-failure-ratio", "cloudislands_storage_failure_ratio");
        metrics.put("permission-cache-hit-ratio", "cloudislands_permission_cache_hit_ratio");
        metrics.put("core-api-error-rate", "cloudislands_core_security_rejects_total");
        return Collections.unmodifiableMap(metrics);
    }
}
