package kr.lunaf.cloudislands.coreservice.metrics;

import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.common.observability.OperationsDashboardPolicy;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusMetricsRendererTest {
    @Test
    void rendersGoalMetricsForOperationsDashboard() {
        RedisIslandJobQueue redisJobs = new RedisIslandJobQueue(URI.create("redis://127.0.0.1:1"));
        PrometheusMetricsRenderer renderer = new PrometheusMetricsRenderer(
            new InMemoryNodeRegistry(2),
            redisJobs,
            new InMemoryRouteTicketStore(Clock.systemUTC()),
            new InMemoryIslandRuntimeRepository(),
            new InMemoryGlobalEventPublisher(),
            Duration.ofSeconds(5),
            () -> 0.125D,
            () -> 1L,
            () -> 2L,
            () -> 3L,
            () -> 4L,
            () -> 5L,
            () -> 6L
        );

        String metrics = renderer.render();

        assertMetric(metrics, "cloudislands_nodes_online");
        assertMetric(metrics, "cloudislands_node_players");
        assertMetric(metrics, "cloudislands_node_mspt");
        assertMetric(metrics, "cloudislands_node_active_islands");
        assertMetric(metrics, "cloudislands_node_activation_queue");
        assertMetric(metrics, "cloudislands_island_activation_seconds");
        assertMetric(metrics, "cloudislands_island_save_seconds");
        assertMetric(metrics, "cloudislands_island_snapshot_seconds");
        assertMetric(metrics, "cloudislands_route_ticket_created_total");
        assertMetric(metrics, "cloudislands_route_ticket_failed_total");
        assertMetric(metrics, "cloudislands_permission_checks_total");
        assertMetric(metrics, "cloudislands_permission_cache_hit_ratio");
        assertMetric(metrics, "cloudislands_jobs_pending");
        assertMetric(metrics, "cloudislands_jobs_failed_total");
        assertMetric(metrics, "cloudislands_jobs_retry_total");
        assertMetric(metrics, "cloudislands_storage_upload_seconds");
        assertMetric(metrics, "cloudislands_storage_download_seconds");
        assertMetric(metrics, "cloudislands_database_query_seconds");
        assertMetric(metrics, "cloudislands_redis_latency_seconds");
        for (String dashboardMetric : OperationsDashboardPolicy.requiredDashboardMetrics().values()) {
            assertMetric(metrics, dashboardMetric);
        }
    }

    private void assertMetric(String metrics, String name) {
        assertTrue(metrics.contains("# HELP " + name + " "), () -> "missing HELP for " + name);
        assertTrue(metrics.contains("# TYPE " + name + " "), () -> "missing TYPE for " + name);
    }
}
