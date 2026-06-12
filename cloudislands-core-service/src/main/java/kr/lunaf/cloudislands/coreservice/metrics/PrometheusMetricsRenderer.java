package kr.lunaf.cloudislands.coreservice.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.DoubleSupplier;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JdbcIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;

public final class PrometheusMetricsRenderer {
    private final NodeRegistry nodes;
    private final IslandJobQueue jobs;
    private final InMemoryGlobalEventPublisher events;
    private final Duration heartbeatTimeout;
    private final DoubleSupplier databaseQuerySeconds;

    public PrometheusMetricsRenderer(NodeRegistry nodes, IslandJobQueue jobs, InMemoryGlobalEventPublisher events, Duration heartbeatTimeout, DoubleSupplier databaseQuerySeconds) {
        this.nodes = nodes;
        this.jobs = jobs;
        this.events = events;
        this.heartbeatTimeout = heartbeatTimeout;
        this.databaseQuerySeconds = databaseQuerySeconds;
    }

    public String render() {
        StringBuilder out = new StringBuilder();
        help(out, "cloudislands_nodes_online", "CloudIslands nodes with fresh heartbeat");
        type(out, "cloudislands_nodes_online", "gauge");
        help(out, "cloudislands_node_players", "Players currently reported by a node");
        type(out, "cloudislands_node_players", "gauge");
        help(out, "cloudislands_node_soft_player_cap", "Soft player capacity before visitor routing is avoided");
        type(out, "cloudislands_node_soft_player_cap", "gauge");
        help(out, "cloudislands_node_hard_player_cap", "Maximum player capacity reported by a node");
        type(out, "cloudislands_node_hard_player_cap", "gauge");
        help(out, "cloudislands_node_mspt", "Node MSPT reported by Paper heartbeat");
        type(out, "cloudislands_node_mspt", "gauge");
        help(out, "cloudislands_node_active_islands", "Active islands currently reported by a node");
        type(out, "cloudislands_node_active_islands", "gauge");
        help(out, "cloudislands_node_max_active_islands", "Maximum active islands supported by a node");
        type(out, "cloudislands_node_max_active_islands", "gauge");
        help(out, "cloudislands_node_activation_queue", "Activation queue depth currently reported by a node");
        type(out, "cloudislands_node_activation_queue", "gauge");
        help(out, "cloudislands_node_max_activation_queue", "Maximum activation queue depth supported by a node");
        type(out, "cloudislands_node_max_activation_queue", "gauge");
        help(out, "cloudislands_node_chunk_load_pressure", "Node chunk loading pressure reported by heartbeat");
        type(out, "cloudislands_node_chunk_load_pressure", "gauge");
        help(out, "cloudislands_node_recent_failure_penalty", "Recent node failure penalty used by routing score");
        type(out, "cloudislands_node_recent_failure_penalty", "gauge");
        help(out, "cloudislands_node_heap_used_mb", "Node JVM heap used in MiB");
        type(out, "cloudislands_node_heap_used_mb", "gauge");
        help(out, "cloudislands_node_heap_max_mb", "Node JVM maximum heap in MiB");
        type(out, "cloudislands_node_heap_max_mb", "gauge");
        help(out, "cloudislands_node_memory_pressure", "Node heap usage divided by max heap");
        type(out, "cloudislands_node_memory_pressure", "gauge");
        help(out, "cloudislands_node_storage_available", "Node object storage availability reported by heartbeat");
        type(out, "cloudislands_node_storage_available", "gauge");
        help(out, "cloudislands_node_routing_score", "Node routing score used by allocator, lower is preferred");
        type(out, "cloudislands_node_routing_score", "gauge");
        help(out, "cloudislands_node_state", "Node state marker by state label");
        type(out, "cloudislands_node_state", "gauge");
        help(out, "cloudislands_permission_cache_hit_ratio", "Paper local permission cache hit ratio reported by heartbeat");
        type(out, "cloudislands_permission_cache_hit_ratio", "gauge");
        help(out, "cloudislands_permission_checks_total", "Paper local island permission checks reported by heartbeat");
        type(out, "cloudislands_permission_checks_total", "counter");
        help(out, "cloudislands_storage_upload_seconds", "Last island storage upload duration reported by Paper heartbeat");
        type(out, "cloudislands_storage_upload_seconds", "gauge");
        help(out, "cloudislands_storage_download_seconds", "Last island storage download duration reported by Paper heartbeat");
        type(out, "cloudislands_storage_download_seconds", "gauge");
        help(out, "cloudislands_island_save_seconds", "Last island save bundle upload duration reported by Paper heartbeat");
        type(out, "cloudislands_island_save_seconds", "gauge");
        help(out, "cloudislands_island_activation_seconds", "Last island activation bundle download duration reported by Paper heartbeat");
        type(out, "cloudislands_island_activation_seconds", "gauge");
        help(out, "cloudislands_island_snapshot_seconds", "Last island snapshot bundle upload duration reported by Paper heartbeat");
        type(out, "cloudislands_island_snapshot_seconds", "gauge");
        Instant now = Instant.now();
        for (NodeLoad node : nodes.snapshot()) {
            boolean fresh = Duration.between(node.lastHeartbeat(), now).compareTo(heartbeatTimeout) <= 0;
            labels(out, "cloudislands_nodes_online", node, null).append(fresh && node.state() != NodeState.DOWN ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_players", node, null).append(node.players()).append('\n');
            labels(out, "cloudislands_node_soft_player_cap", node, null).append(node.softPlayerCap()).append('\n');
            labels(out, "cloudislands_node_hard_player_cap", node, null).append(node.hardPlayerCap()).append('\n');
            labels(out, "cloudislands_node_mspt", node, null).append(node.mspt()).append('\n');
            labels(out, "cloudislands_node_active_islands", node, null).append(node.activeIslands()).append('\n');
            labels(out, "cloudislands_node_max_active_islands", node, null).append(node.maxActiveIslands()).append('\n');
            labels(out, "cloudislands_node_activation_queue", node, null).append(node.activationQueue()).append('\n');
            labels(out, "cloudislands_node_max_activation_queue", node, null).append(node.maxActivationQueue()).append('\n');
            labels(out, "cloudislands_node_chunk_load_pressure", node, null).append(node.chunkLoadPressure()).append('\n');
            labels(out, "cloudislands_node_recent_failure_penalty", node, null).append(node.recentFailurePenalty()).append('\n');
            labels(out, "cloudislands_node_heap_used_mb", node, null).append(node.heapUsedMb()).append('\n');
            labels(out, "cloudislands_node_heap_max_mb", node, null).append(node.heapMaxMb()).append('\n');
            labels(out, "cloudislands_node_memory_pressure", node, null).append(memoryPressure(node)).append('\n');
            labels(out, "cloudislands_node_storage_available", node, null).append(node.storageAvailable() ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_routing_score", node, null).append(node.score()).append('\n');
            for (NodeState state : NodeState.values()) {
                labels(out, "cloudislands_node_state", node, "state=\"" + state.name() + "\"").append(node.state() == state ? 1 : 0).append('\n');
            }
            String permissionHitRatio = node.heartbeatMetadata().get("permissionCacheHitRatio");
            if (permissionHitRatio != null && !permissionHitRatio.isBlank()) {
                labels(out, "cloudislands_permission_cache_hit_ratio", node, null).append(permissionHitRatio).append('\n');
            }
            appendMetadataGauge(out, "cloudislands_permission_checks_total", node, "permissionChecks");
            appendMetadataGauge(out, "cloudislands_storage_upload_seconds", node, "storageUploadSeconds");
            appendMetadataGauge(out, "cloudislands_storage_download_seconds", node, "storageDownloadSeconds");
            appendMetadataGauge(out, "cloudislands_island_save_seconds", node, "storageUploadSeconds");
            appendMetadataGauge(out, "cloudislands_island_activation_seconds", node, "storageDownloadSeconds");
            appendMetadataGauge(out, "cloudislands_island_snapshot_seconds", node, "storageUploadSeconds");
        }
        help(out, "cloudislands_jobs_total", "Island jobs by in-memory state or backend mode");
        type(out, "cloudislands_jobs_total", "gauge");
        Map<String, Long> jobCounts;
        String jobBackend;
        long jobRetries;
        double redisLatencySeconds = Double.NaN;
        if (jobs instanceof InMemoryIslandJobPublisher memoryJobs) {
            jobCounts = memoryJobs.countsByState();
            jobBackend = "memory";
            jobRetries = memoryJobs.retryAttemptsTotal();
        } else if (jobs instanceof JdbcIslandJobQueue jdbcJobs) {
            jobCounts = jdbcJobs.countsByState();
            jobBackend = "jdbc";
            jobRetries = jdbcJobs.retryAttemptsTotal();
        } else if (jobs instanceof RedisIslandJobQueue redisJobs) {
            jobCounts = redisJobs.countsByState();
            jobBackend = "redis";
            jobRetries = redisJobs.retryAttemptsTotal();
            redisLatencySeconds = redisJobs.latencySeconds();
        } else {
            jobCounts = Map.of("PENDING", 0L, "CLAIMED", 0L, "COMPLETED", 0L, "FAILED", 0L, "CANCELED", 0L);
            jobBackend = "external";
            jobRetries = 0L;
        }
        for (Map.Entry<String, Long> entry : jobCounts.entrySet()) {
            out.append("cloudislands_jobs_total{state=\"").append(entry.getKey()).append("\",backend=\"").append(jobBackend).append("\"} ").append(entry.getValue()).append('\n');
        }
        help(out, "cloudislands_jobs_pending", "Island jobs waiting for a node claim");
        type(out, "cloudislands_jobs_pending", "gauge");
        out.append("cloudislands_jobs_pending{backend=\"").append(jobBackend).append("\"} ").append(jobCounts.getOrDefault("PENDING", 0L)).append('\n');
        help(out, "cloudislands_jobs_failed_total", "Island jobs that reached failed state");
        type(out, "cloudislands_jobs_failed_total", "gauge");
        out.append("cloudislands_jobs_failed_total{backend=\"").append(jobBackend).append("\"} ").append(jobCounts.getOrDefault("FAILED", 0L)).append('\n');
        help(out, "cloudislands_jobs_retry_total", "Island job retry attempts recorded by the queue");
        type(out, "cloudislands_jobs_retry_total", "counter");
        out.append("cloudislands_jobs_retry_total{backend=\"").append(jobBackend).append("\"} ").append(jobRetries).append('\n');
        help(out, "cloudislands_database_query_seconds", "Last JDBC query duration observed by Core API");
        type(out, "cloudislands_database_query_seconds", "gauge");
        out.append("cloudislands_database_query_seconds ").append(databaseQuerySeconds.getAsDouble()).append('\n');
        if (!Double.isNaN(redisLatencySeconds)) {
            help(out, "cloudislands_redis_latency_seconds", "Redis PING latency observed by Core API");
            type(out, "cloudislands_redis_latency_seconds", "gauge");
            out.append("cloudislands_redis_latency_seconds ").append(redisLatencySeconds).append('\n');
        }
        help(out, "cloudislands_route_ticket_created_total", "Route tickets created by Core API");
        type(out, "cloudislands_route_ticket_created_total", "counter");
        out.append("cloudislands_route_ticket_created_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CREATED.name())).append('\n');
        help(out, "cloudislands_route_ticket_consumed_total", "Route tickets consumed by Paper nodes");
        type(out, "cloudislands_route_ticket_consumed_total", "counter");
        out.append("cloudislands_route_ticket_consumed_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CONSUMED.name())).append('\n');
        help(out, "cloudislands_route_ticket_failed_total", "Route ticket failures recorded by Core API");
        type(out, "cloudislands_route_ticket_failed_total", "counter");
        out.append("cloudislands_route_ticket_failed_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED.name())).append('\n');
        eventCounter(out, "cloudislands_island_activation_requested_total", "Island activation requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ACTIVATE_REQUESTED);
        eventCounter(out, "cloudislands_island_activated_total", "Island activations completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ACTIVATED);
        eventCounter(out, "cloudislands_island_deactivated_total", "Island deactivations completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_DEACTIVATED);
        eventCounter(out, "cloudislands_island_snapshot_requested_total", "Island snapshot requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_SNAPSHOT_REQUESTED);
        eventCounter(out, "cloudislands_island_snapshot_created_total", "Island snapshots completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_SNAPSHOT_CREATED);
        eventCounter(out, "cloudislands_island_migrated_total", "Island migrations completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_MIGRATED);
        eventCounter(out, "cloudislands_island_recovery_required_total", "Islands marked for recovery after node failure", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RECOVERY_REQUIRED);
        eventCounter(out, "cloudislands_island_level_updated_total", "Island level recalculations completed by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_LEVEL_UPDATED);
        eventCounter(out, "cloudislands_island_blocks_changed_total", "Island block delta updates received by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BLOCKS_CHANGED);
        eventCounter(out, "cloudislands_island_bank_changed_total", "Island bank balance changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BANK_CHANGED);
        eventCounter(out, "cloudislands_island_upgrade_total", "Island upgrades purchased through Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_UPGRADE);
        eventCounter(out, "cloudislands_island_limit_changed_total", "Island limit changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_LIMIT_CHANGED);
        eventCounter(out, "cloudislands_island_mission_completed_total", "Island missions completed through Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_MISSION_COMPLETED);
        return out.toString();
    }

    private static void help(StringBuilder out, String name, String description) {
        out.append("# HELP ").append(name).append(' ').append(description).append('\n');
    }

    private static void type(StringBuilder out, String name, String type) {
        out.append("# TYPE ").append(name).append(' ').append(type).append('\n');
    }

    private static StringBuilder labels(StringBuilder out, String name, NodeLoad node, String extra) {
        out.append(name).append("{node=\"").append(escape(node.nodeId())).append("\",server=\"").append(escape(node.velocityServerName())).append("\"");
        if (extra != null && !extra.isBlank()) {
            out.append(',').append(extra);
        }
        return out.append("} ");
    }

    private void eventCounter(StringBuilder out, String name, String description, kr.lunaf.cloudislands.common.event.CloudIslandEventType eventType) {
        help(out, name, description);
        type(out, name, "counter");
        out.append(name).append(' ').append(events.countByType(eventType.name())).append('\n');
    }

    private void appendMetadataGauge(StringBuilder out, String name, NodeLoad node, String metadataKey) {
        String value = node.heartbeatMetadata().get(metadataKey);
        if (value != null && !value.isBlank()) {
            labels(out, name, node, null).append(value).append('\n');
        }
    }

    private static double memoryPressure(NodeLoad node) {
        return node.heapMaxMb() <= 0 ? 1.0D : Math.min((double) node.heapUsedMb() / node.heapMaxMb(), 1.5D);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
