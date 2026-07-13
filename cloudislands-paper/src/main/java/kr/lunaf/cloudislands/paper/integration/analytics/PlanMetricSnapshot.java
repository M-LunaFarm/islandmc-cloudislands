package kr.lunaf.cloudislands.paper.integration.analytics;

import java.time.Instant;
import kr.lunaf.cloudislands.coreclient.AdminMetricsSummaryView;

public record PlanMetricSnapshot(
    long coreMetricSamples,
    long clusterActiveIslands,
    long clusterPlayers,
    long clusterOnlineNodes,
    long pendingJobs,
    long failedJobs,
    long localActiveIslands,
    long localOnlinePlayers,
    long refreshedAtEpochSeconds
) {
    private static final String CLUSTER_ACTIVE_ISLANDS = "cloudislands_cluster_active_islands";
    private static final String CLUSTER_PLAYERS = "cloudislands_cluster_players";
    private static final String CLUSTER_ONLINE_NODES = "cloudislands_cluster_nodes_online";
    private static final String PENDING_JOBS = "cloudislands_jobs_pending";
    private static final String FAILED_JOBS = "cloudislands_jobs_failed_total";

    public PlanMetricSnapshot {
        coreMetricSamples = Math.max(0L, coreMetricSamples);
        clusterActiveIslands = Math.max(0L, clusterActiveIslands);
        clusterPlayers = Math.max(0L, clusterPlayers);
        clusterOnlineNodes = Math.max(0L, clusterOnlineNodes);
        pendingJobs = Math.max(0L, pendingJobs);
        failedJobs = Math.max(0L, failedJobs);
        localActiveIslands = Math.max(0L, localActiveIslands);
        localOnlinePlayers = Math.max(0L, localOnlinePlayers);
        refreshedAtEpochSeconds = Math.max(0L, refreshedAtEpochSeconds);
    }

    public static PlanMetricSnapshot empty() {
        return new PlanMetricSnapshot(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    public static PlanMetricSnapshot from(AdminMetricsSummaryView metrics, int localActiveIslands, int localOnlinePlayers, Instant refreshedAt) {
        AdminMetricsSummaryView safeMetrics = metrics == null
            ? new AdminMetricsSummaryView(0L, java.util.List.of(), java.util.Map.of())
            : metrics;
        Instant safeRefreshedAt = refreshedAt == null ? Instant.EPOCH : refreshedAt;
        return new PlanMetricSnapshot(
            safeMetrics.samples(),
            metric(safeMetrics, CLUSTER_ACTIVE_ISLANDS),
            metric(safeMetrics, CLUSTER_PLAYERS),
            metric(safeMetrics, CLUSTER_ONLINE_NODES),
            metric(safeMetrics, PENDING_JOBS),
            metric(safeMetrics, FAILED_JOBS),
            localActiveIslands,
            localOnlinePlayers,
            safeRefreshedAt.getEpochSecond()
        );
    }

    private static long metric(AdminMetricsSummaryView metrics, String name) {
        double value = metrics.value(name);
        if (!Double.isFinite(value) || value <= 0.0D) {
            return 0L;
        }
        return Math.round(value);
    }
}
