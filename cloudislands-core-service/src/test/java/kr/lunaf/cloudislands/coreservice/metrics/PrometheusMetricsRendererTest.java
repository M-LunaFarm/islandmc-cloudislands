package kr.lunaf.cloudislands.coreservice.metrics;

import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.common.observability.OperationsDashboardPolicy;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusMetricsRendererTest {
    @Test
    void rendersGoalMetricsForOperationsDashboard() {
        RedisIslandJobQueue redisJobs = new RedisIslandJobQueue(URI.create("redis://127.0.0.1:1"));
        InMemoryNodeRegistry nodes = new InMemoryNodeRegistry(2);
        seedDashboardMetricHeartbeat(nodes);
        PrometheusMetricsRenderer renderer = new PrometheusMetricsRenderer(
            nodes,
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
            assertSample(metrics, dashboardMetric);
        }
    }

    @Test
    void renderedMetricFamiliesHaveConcreteUpdateSources() throws Exception {
        Path root = workspaceRoot();
        String renderer = read(root, "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/metrics/PrometheusMetricsRenderer.java");
        String sources = String.join("\n",
            renderer,
            read(root, "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/CloudIslandsCoreApplication.java"),
            read(root, "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/metrics/CoreMetricsFactory.java"),
            read(root, "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/db/MeteredDataSource.java"),
            read(root, "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/CoreHttpRouteRegistrar.java"),
            read(root, "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/ranking/DirtyRankingRecalculationTask.java"),
            read(root, "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/routing/NodeLoad.java"),
            read(root, "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/PaperObservabilityFormatter.java")
        );
        List<MetricUpdateSource> updateSources = List.of(
            new MetricUpdateSource("http-security-reject-counter", name -> name.startsWith("cloudislands_core_security_rejects"), List.of("securityRejectsTotal.incrementAndGet()", "routeRegistrar::securityRejectsTotal")),
            new MetricUpdateSource("metered-datasource", name -> name.startsWith("cloudislands_database_"), List.of("MeteredDataSource", "openedConnections.incrementAndGet()", "queryFailures.incrementAndGet()")),
            new MetricUpdateSource("job-queue-state", name -> name.startsWith("cloudislands_jobs_"), List.of("countsByState()", "retryAttemptsTotal()")),
            new MetricUpdateSource("route-event-or-store", name -> name.startsWith("cloudislands_route_"), List.of("events.countByType", "tickets.countsByState()")),
            new MetricUpdateSource("redis-client-or-cache", name -> name.startsWith("cloudislands_redis_"), List.of("redisJobs.latencySeconds()", "redisEventPublisher.failuresTotal()", "redisCacheFailures(")),
            new MetricUpdateSource("ranking-worker", name -> name.startsWith("cloudislands_ranking_"), List.of("DirtyRankingRecalculationTask", "drainedTotal.addAndGet", "recalculatedTotal.incrementAndGet")),
            new MetricUpdateSource("node-heartbeat", name -> name.startsWith("cloudislands_node_") || name.startsWith("cloudislands_nodes_") || name.startsWith("cloudislands_cluster_") || name.startsWith("cloudislands_pool_"), List.of("nodes.snapshot()", "NodeLoad", "heartbeatMetadata()")),
            new MetricUpdateSource("paper-heartbeat-metadata", name -> name.startsWith("cloudislands_paper_"), List.of("PaperObservabilityFormatter", "appendMetadataGauge", "routeSessions.proxySourceRejections()")),
            new MetricUpdateSource("permission-cache-heartbeat", name -> name.startsWith("cloudislands_permission_"), List.of("permissionCache().lookupCount()", "permissionCache().hitRatio()", "appendMetadataGauge")),
            new MetricUpdateSource("storage-heartbeat", name -> name.startsWith("cloudislands_storage_"), List.of("MeteredIslandStorage", "storage.lastUploadSeconds()", "appendMetadataGauge")),
            new MetricUpdateSource("runtime-state-repository", name -> name.equals("cloudislands_island_runtimes_total"), List.of("runtimes.countsByState()")),
            new MetricUpdateSource("core-event-publisher", name -> name.startsWith("cloudislands_island_") || name.startsWith("cloudislands_addon_") || name.equals("cloudislands_node_state_changed_total"), List.of("eventCounter(out", "events.countByType", "appendEventFieldCounters")),
            new MetricUpdateSource("core-config-supplier", name -> name.startsWith("cloudislands_core_") || name.startsWith("cloudislands_admin_") || name.startsWith("cloudislands_postgresql_") || name.startsWith("cloudislands_object_storage_"), List.of("CoreMetricsFactory", "config::", "config.rateLimitRequests()"))
        );

        Set<String> uncovered = new LinkedHashSet<>();
        for (String metric : metricNames(renderer)) {
            MetricUpdateSource source = updateSources.stream()
                .filter(candidate -> candidate.matches(metric))
                .findFirst()
                .orElse(null);
            if (source == null) {
                uncovered.add(metric);
                continue;
            }
            for (String signal : source.requiredSignals()) {
                assertTrue(sources.contains(signal), metric + " source category " + source.name() + " must keep signal " + signal);
            }
        }
        assertTrue(uncovered.isEmpty(), "Metrics without update-source coverage: " + uncovered);
    }

    private void assertMetric(String metrics, String name) {
        assertTrue(metrics.contains("# HELP " + name + " "), () -> "missing HELP for " + name);
        assertTrue(metrics.contains("# TYPE " + name + " "), () -> "missing TYPE for " + name);
    }

    private void assertSample(String metrics, String name) {
        assertTrue(Pattern.compile("(?m)^" + Pattern.quote(name) + "(\\{|\\s)").matcher(metrics).find(), () -> "missing sample for " + name);
    }

    private void seedDashboardMetricHeartbeat(InMemoryNodeRegistry nodes) {
        String metadata = "*"
            + ";storageUploadSeconds=0.25"
            + ";storageDownloadSeconds=0.50"
            + ";periodicSaveFailures=1"
            + ";emptySaveFailures=1"
            + ";permissionChecks=12"
            + ";permissionCacheHitRatio=0.75"
            + ";redisAvailable=false"
            + ";redisLatencySeconds=0.012"
            + ";redisFailures=2";
        nodes.heartbeat(new NodeHeartbeatRequest(
            NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
            "island-metric-1",
            "island",
            "Island-Metric-1",
            "1.21.8",
            NodeState.READY,
            8,
            80,
            100,
            10,
            32,
            200,
            18.5D,
            2,
            20,
            0.15D,
            2048L,
            4096L,
            0,
            true,
            metadata
        ));
    }

    private Set<String> metricNames(String source) {
        Pattern pattern = Pattern.compile("\"(cloudislands_[A-Za-z0-9_]+)\"");
        Matcher matcher = pattern.matcher(source);
        Set<String> names = new LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private Path workspaceRoot() {
        Path current = Path.of("").toAbsolutePath();
        if (Files.isDirectory(current.resolve("cloudislands-core-service"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("cloudislands-core-service"))) {
            return parent;
        }
        throw new IllegalStateException("Unable to locate workspace root from " + current);
    }

    private String read(Path root, String relativePath) throws Exception {
        return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private record MetricUpdateSource(String name, Predicate<String> matcher, List<String> requiredSignals) {
        boolean matches(String metricName) {
            return matcher.test(metricName);
        }
    }
}
