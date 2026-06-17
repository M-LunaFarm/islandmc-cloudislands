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
        metrics.put("island-save-failures", "cloudislands_island_save_failures_total");
        metrics.put("route-failures", "cloudislands_route_ticket_failed_total");
        metrics.put("redis-latency", "cloudislands_redis_latency_seconds");
        metrics.put("database-connection-pool-usage", "cloudislands_database_connection_pool_usage_ratio");
        metrics.put("database-connection-pool-available", "cloudislands_database_connections_available");
        metrics.put("database-connection-pool-saturated", "cloudislands_database_connection_pool_saturated");
        metrics.put("object-storage-failure-ratio", "cloudislands_cluster_storage_failure_ratio");
        metrics.put("object-storage-node-failure-ratio", "cloudislands_storage_failure_ratio");
        return Collections.unmodifiableMap(metrics);
    }
}
