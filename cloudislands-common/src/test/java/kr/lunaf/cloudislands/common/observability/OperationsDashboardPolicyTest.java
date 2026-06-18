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
                Map.entry("island-save-failures", "cloudislands_island_save_failures_total"),
                Map.entry("route-failures", "cloudislands_route_ticket_failed_total"),
                Map.entry("redis-latency", "cloudislands_redis_latency_seconds"),
                Map.entry("database-connection-pool-usage", "cloudislands_database_connection_pool_usage_ratio"),
                Map.entry("database-connection-pool-available", "cloudislands_database_connections_available"),
                Map.entry("database-connection-pool-saturated", "cloudislands_database_connection_pool_saturated"),
                Map.entry("object-storage-failure-ratio", "cloudislands_cluster_storage_failure_ratio"),
                Map.entry("object-storage-node-failure-ratio", "cloudislands_storage_failure_ratio")
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
