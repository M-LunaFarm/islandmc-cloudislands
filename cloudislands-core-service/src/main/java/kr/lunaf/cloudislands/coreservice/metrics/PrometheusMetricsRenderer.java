package kr.lunaf.cloudislands.coreservice.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.JdbcIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.job.redis.RedisIslandJobQueue;
import kr.lunaf.cloudislands.coreservice.repository.IslandRuntimeRepository;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;

public final class PrometheusMetricsRenderer {
    private final NodeRegistry nodes;
    private final IslandJobQueue jobs;
    private final RouteTicketStore tickets;
    private final IslandRuntimeRepository runtimes;
    private final InMemoryGlobalEventPublisher events;
    private final Duration heartbeatTimeout;
    private final DoubleSupplier databaseQuerySeconds;
    private final LongSupplier databaseActiveConnections;
    private final LongSupplier databaseMaxConnections;
    private final LongSupplier databaseOpenedConnections;
    private final LongSupplier databaseConnectionFailures;
    private final LongSupplier databaseQueryFailures;
    private final LongSupplier redisEventFailures;
    private final LongSupplier redisCacheFailures;
    private final BooleanSupplier coreTokenConfigured;
    private final BooleanSupplier adminTokenConfigured;
    private final BooleanSupplier adminApiEnabled;
    private final BooleanSupplier mtlsRequired;
    private final BooleanSupplier ipAllowlistEnabled;
    private final BooleanSupplier publicBindWithoutIpAllowlist;
    private final BooleanSupplier redisPublicHost;
    private final BooleanSupplier postgresqlPublicHost;
    private final BooleanSupplier objectStoragePublicHost;
    private final BooleanSupplier objectStoragePlainHttpPublicHost;
    private final LongSupplier rateLimitRequests;
    private final LongSupplier rateLimitWindowSeconds;
    private final LongSupplier rankingDirtyDrainedTotal;
    private final LongSupplier rankingRecalculatedTotal;
    private final LongSupplier rankingRecalculationFailuresTotal;
    private final LongSupplier rankingRecalculationLastBatchSize;
    private final LongSupplier securityRejectsTotal;
    private final LongSupplier securityRejectsRateLimited;
    private final LongSupplier securityRejectsUnauthorized;
    private final LongSupplier securityRejectsMtlsRequired;
    private final LongSupplier securityRejectsIpNotAllowed;
    private final LongSupplier securityRejectsAdminPermissionDenied;

    public PrometheusMetricsRenderer(NodeRegistry nodes, IslandJobQueue jobs, RouteTicketStore tickets, IslandRuntimeRepository runtimes, InMemoryGlobalEventPublisher events, Duration heartbeatTimeout, DoubleSupplier databaseQuerySeconds, LongSupplier databaseActiveConnections, LongSupplier databaseOpenedConnections, LongSupplier databaseConnectionFailures, LongSupplier databaseQueryFailures, LongSupplier redisEventFailures, LongSupplier redisCacheFailures) {
        this(nodes, jobs, tickets, runtimes, events, heartbeatTimeout, databaseQuerySeconds, databaseActiveConnections, databaseOpenedConnections, databaseConnectionFailures, databaseQueryFailures, redisEventFailures, redisCacheFailures, () -> 0L, () -> false, () -> false, () -> false, () -> false, () -> false, () -> false, () -> false, () -> false, () -> false, () -> false, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L);
    }

    public PrometheusMetricsRenderer(NodeRegistry nodes, IslandJobQueue jobs, RouteTicketStore tickets, IslandRuntimeRepository runtimes, InMemoryGlobalEventPublisher events, Duration heartbeatTimeout, DoubleSupplier databaseQuerySeconds, LongSupplier databaseActiveConnections, LongSupplier databaseOpenedConnections, LongSupplier databaseConnectionFailures, LongSupplier databaseQueryFailures, LongSupplier redisEventFailures, LongSupplier redisCacheFailures, BooleanSupplier coreTokenConfigured, BooleanSupplier adminTokenConfigured, BooleanSupplier adminApiEnabled, BooleanSupplier mtlsRequired, BooleanSupplier ipAllowlistEnabled, BooleanSupplier publicBindWithoutIpAllowlist, BooleanSupplier redisPublicHost, BooleanSupplier postgresqlPublicHost, BooleanSupplier objectStoragePublicHost, BooleanSupplier objectStoragePlainHttpPublicHost) {
        this(nodes, jobs, tickets, runtimes, events, heartbeatTimeout, databaseQuerySeconds, databaseActiveConnections, databaseOpenedConnections, databaseConnectionFailures, databaseQueryFailures, redisEventFailures, redisCacheFailures, () -> 0L, coreTokenConfigured, adminTokenConfigured, adminApiEnabled, mtlsRequired, ipAllowlistEnabled, publicBindWithoutIpAllowlist, redisPublicHost, postgresqlPublicHost, objectStoragePublicHost, objectStoragePlainHttpPublicHost, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L, () -> 0L);
    }

    public PrometheusMetricsRenderer(NodeRegistry nodes, IslandJobQueue jobs, RouteTicketStore tickets, IslandRuntimeRepository runtimes, InMemoryGlobalEventPublisher events, Duration heartbeatTimeout, DoubleSupplier databaseQuerySeconds, LongSupplier databaseActiveConnections, LongSupplier databaseOpenedConnections, LongSupplier databaseConnectionFailures, LongSupplier databaseQueryFailures, LongSupplier redisEventFailures, LongSupplier redisCacheFailures, LongSupplier databaseMaxConnections, BooleanSupplier coreTokenConfigured, BooleanSupplier adminTokenConfigured, BooleanSupplier adminApiEnabled, BooleanSupplier mtlsRequired, BooleanSupplier ipAllowlistEnabled, BooleanSupplier publicBindWithoutIpAllowlist, BooleanSupplier redisPublicHost, BooleanSupplier postgresqlPublicHost, BooleanSupplier objectStoragePublicHost, BooleanSupplier objectStoragePlainHttpPublicHost, LongSupplier rateLimitRequests, LongSupplier rateLimitWindowSeconds, LongSupplier rankingDirtyDrainedTotal, LongSupplier rankingRecalculatedTotal, LongSupplier rankingRecalculationFailuresTotal, LongSupplier rankingRecalculationLastBatchSize, LongSupplier securityRejectsTotal, LongSupplier securityRejectsRateLimited, LongSupplier securityRejectsUnauthorized, LongSupplier securityRejectsMtlsRequired, LongSupplier securityRejectsIpNotAllowed, LongSupplier securityRejectsAdminPermissionDenied) {
        this.nodes = nodes;
        this.jobs = jobs;
        this.tickets = tickets;
        this.runtimes = runtimes;
        this.events = events;
        this.heartbeatTimeout = heartbeatTimeout;
        this.databaseQuerySeconds = databaseQuerySeconds;
        this.databaseActiveConnections = databaseActiveConnections;
        this.databaseMaxConnections = databaseMaxConnections;
        this.databaseOpenedConnections = databaseOpenedConnections;
        this.databaseConnectionFailures = databaseConnectionFailures;
        this.databaseQueryFailures = databaseQueryFailures;
        this.redisEventFailures = redisEventFailures;
        this.redisCacheFailures = redisCacheFailures;
        this.coreTokenConfigured = coreTokenConfigured;
        this.adminTokenConfigured = adminTokenConfigured;
        this.adminApiEnabled = adminApiEnabled;
        this.mtlsRequired = mtlsRequired;
        this.ipAllowlistEnabled = ipAllowlistEnabled;
        this.publicBindWithoutIpAllowlist = publicBindWithoutIpAllowlist;
        this.redisPublicHost = redisPublicHost;
        this.postgresqlPublicHost = postgresqlPublicHost;
        this.objectStoragePublicHost = objectStoragePublicHost;
        this.objectStoragePlainHttpPublicHost = objectStoragePlainHttpPublicHost;
        this.rateLimitRequests = rateLimitRequests;
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
        this.rankingDirtyDrainedTotal = rankingDirtyDrainedTotal;
        this.rankingRecalculatedTotal = rankingRecalculatedTotal;
        this.rankingRecalculationFailuresTotal = rankingRecalculationFailuresTotal;
        this.rankingRecalculationLastBatchSize = rankingRecalculationLastBatchSize;
        this.securityRejectsTotal = securityRejectsTotal;
        this.securityRejectsRateLimited = securityRejectsRateLimited;
        this.securityRejectsUnauthorized = securityRejectsUnauthorized;
        this.securityRejectsMtlsRequired = securityRejectsMtlsRequired;
        this.securityRejectsIpNotAllowed = securityRejectsIpNotAllowed;
        this.securityRejectsAdminPermissionDenied = securityRejectsAdminPermissionDenied;
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
        help(out, "cloudislands_node_reserved_slots", "Reserved player slots reported by a node");
        type(out, "cloudislands_node_reserved_slots", "gauge");
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
        help(out, "cloudislands_node_activation_eligible", "Whether a node can receive new island activations");
        type(out, "cloudislands_node_activation_eligible", "gauge");
        help(out, "cloudislands_node_state", "Node state marker by state label");
        type(out, "cloudislands_node_state", "gauge");
        help(out, "cloudislands_cluster_nodes_online", "Total CloudIslands nodes with fresh heartbeat");
        type(out, "cloudislands_cluster_nodes_online", "gauge");
        help(out, "cloudislands_cluster_players", "Total players across fresh CloudIslands nodes");
        type(out, "cloudislands_cluster_players", "gauge");
        help(out, "cloudislands_cluster_active_islands", "Total active islands across fresh CloudIslands nodes");
        type(out, "cloudislands_cluster_active_islands", "gauge");
        help(out, "cloudislands_cluster_activation_queue", "Total activation queue depth across fresh CloudIslands nodes");
        type(out, "cloudislands_cluster_activation_queue", "gauge");
        help(out, "cloudislands_cluster_storage_available_nodes", "Fresh nodes reporting object storage availability");
        type(out, "cloudislands_cluster_storage_available_nodes", "gauge");
        help(out, "cloudislands_cluster_activation_eligible_nodes", "Fresh nodes currently eligible for island activation");
        type(out, "cloudislands_cluster_activation_eligible_nodes", "gauge");
        help(out, "cloudislands_cluster_max_mspt", "Highest MSPT reported by fresh CloudIslands nodes");
        type(out, "cloudislands_cluster_max_mspt", "gauge");
        help(out, "cloudislands_permission_cache_hit_ratio", "Paper local permission cache hit ratio reported by heartbeat");
        type(out, "cloudislands_permission_cache_hit_ratio", "gauge");
        help(out, "cloudislands_permission_checks_total", "Paper local island permission checks reported by heartbeat");
        type(out, "cloudislands_permission_checks_total", "counter");
        help(out, "cloudislands_storage_upload_seconds", "Last island storage upload duration reported by Paper heartbeat");
        type(out, "cloudislands_storage_upload_seconds", "gauge");
        help(out, "cloudislands_storage_download_seconds", "Last island storage download duration reported by Paper heartbeat");
        type(out, "cloudislands_storage_download_seconds", "gauge");
        help(out, "cloudislands_storage_failures_total", "Island object storage failures reported by Paper heartbeat");
        type(out, "cloudislands_storage_failures_total", "counter");
        help(out, "cloudislands_island_save_seconds", "Last island save bundle upload duration reported by Paper heartbeat");
        type(out, "cloudislands_island_save_seconds", "gauge");
        help(out, "cloudislands_island_activation_seconds", "Last island activation bundle download duration reported by Paper heartbeat");
        type(out, "cloudislands_island_activation_seconds", "gauge");
        help(out, "cloudislands_island_snapshot_seconds", "Last island snapshot bundle upload duration reported by Paper heartbeat");
        type(out, "cloudislands_island_snapshot_seconds", "gauge");
        help(out, "cloudislands_paper_periodic_save_retry_queue", "Periodic island saves waiting for retry on Paper nodes");
        type(out, "cloudislands_paper_periodic_save_retry_queue", "gauge");
        help(out, "cloudislands_paper_empty_save_retry_queue", "Empty island saves waiting for retry before node-local deactivation");
        type(out, "cloudislands_paper_empty_save_retry_queue", "gauge");
        help(out, "cloudislands_island_save_failures_total", "Island save failures observed by Paper nodes");
        type(out, "cloudislands_island_save_failures_total", "counter");
        help(out, "cloudislands_paper_periodic_save_failures_total", "Periodic island save failures observed by Paper nodes");
        type(out, "cloudislands_paper_periodic_save_failures_total", "counter");
        help(out, "cloudislands_paper_empty_save_failures_total", "Empty island save failures observed before node-local deactivation");
        type(out, "cloudislands_paper_empty_save_failures_total", "counter");
        help(out, "cloudislands_paper_proxy_source_rejections_total", "Paper logins rejected because the source was not an allowed proxy");
        type(out, "cloudislands_paper_proxy_source_rejections_total", "counter");
        help(out, "cloudislands_paper_forwarding_rejections_total", "Paper logins rejected because Velocity forwarding security was not ready");
        type(out, "cloudislands_paper_forwarding_rejections_total", "counter");
        help(out, "cloudislands_paper_route_session_rejections_total", "Paper logins or joins rejected because no valid route session was present");
        type(out, "cloudislands_paper_route_session_rejections_total", "counter");
        help(out, "cloudislands_paper_route_session_check_failures_total", "Paper route session verification failures while checking Core API");
        type(out, "cloudislands_paper_route_session_check_failures_total", "counter");
        help(out, "cloudislands_paper_chat_broadcasts_total", "Island chat events broadcast by Paper nodes");
        type(out, "cloudislands_paper_chat_broadcasts_total", "counter");
        help(out, "cloudislands_paper_chat_deliveries_total", "Island chat recipient deliveries performed by Paper nodes");
        type(out, "cloudislands_paper_chat_deliveries_total", "counter");
        help(out, "cloudislands_paper_chat_no_recipient_broadcasts_total", "Island chat broadcasts with no online recipient on Paper nodes");
        type(out, "cloudislands_paper_chat_no_recipient_broadcasts_total", "counter");
        help(out, "cloudislands_core_token_configured", "Whether Core API token authentication has a configured token");
        type(out, "cloudislands_core_token_configured", "gauge");
        out.append("cloudislands_core_token_configured ").append(coreTokenConfigured.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_admin_token_configured", "Whether admin API token authentication has a configured token");
        type(out, "cloudislands_admin_token_configured", "gauge");
        out.append("cloudislands_admin_token_configured ").append(adminTokenConfigured.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_admin_api_enabled", "Whether Core admin API endpoints are enabled");
        type(out, "cloudislands_admin_api_enabled", "gauge");
        out.append("cloudislands_admin_api_enabled ").append(adminApiEnabled.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_core_mtls_required", "Whether Core API mTLS verification is required");
        type(out, "cloudislands_core_mtls_required", "gauge");
        out.append("cloudislands_core_mtls_required ").append(mtlsRequired.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_core_ip_allowlist_enabled", "Whether Core API IP allowlist is configured");
        type(out, "cloudislands_core_ip_allowlist_enabled", "gauge");
        out.append("cloudislands_core_ip_allowlist_enabled ").append(ipAllowlistEnabled.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_core_rate_limit_requests", "Configured Core API fixed-window request limit per remote address, or zero when disabled");
        type(out, "cloudislands_core_rate_limit_requests", "gauge");
        out.append("cloudislands_core_rate_limit_requests ").append(Math.max(0L, rateLimitRequests.getAsLong())).append('\n');
        help(out, "cloudislands_core_rate_limit_window_seconds", "Configured Core API fixed-window rate limit duration in seconds");
        type(out, "cloudislands_core_rate_limit_window_seconds", "gauge");
        out.append("cloudislands_core_rate_limit_window_seconds ").append(Math.max(0L, rateLimitWindowSeconds.getAsLong())).append('\n');
        help(out, "cloudislands_core_public_bind_without_ip_allowlist", "Whether Core API is publicly bound without an IP allowlist");
        type(out, "cloudislands_core_public_bind_without_ip_allowlist", "gauge");
        out.append("cloudislands_core_public_bind_without_ip_allowlist ").append(publicBindWithoutIpAllowlist.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_redis_public_host", "Whether Redis is configured with a non-internal host");
        type(out, "cloudislands_redis_public_host", "gauge");
        out.append("cloudislands_redis_public_host ").append(redisPublicHost.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_postgresql_public_host", "Whether PostgreSQL is configured with a non-internal host");
        type(out, "cloudislands_postgresql_public_host", "gauge");
        out.append("cloudislands_postgresql_public_host ").append(postgresqlPublicHost.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_object_storage_public_host", "Whether object storage endpoint is configured with a non-internal host");
        type(out, "cloudislands_object_storage_public_host", "gauge");
        out.append("cloudislands_object_storage_public_host ").append(objectStoragePublicHost.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_object_storage_plain_http_public_host", "Whether object storage uses plain HTTP on a non-internal host");
        type(out, "cloudislands_object_storage_plain_http_public_host", "gauge");
        out.append("cloudislands_object_storage_plain_http_public_host ").append(objectStoragePlainHttpPublicHost.getAsBoolean() ? 1 : 0).append('\n');
        help(out, "cloudislands_core_security_rejects_total", "Core API requests rejected by security gates");
        type(out, "cloudislands_core_security_rejects_total", "counter");
        out.append("cloudislands_core_security_rejects_total ").append(Math.max(0L, securityRejectsTotal.getAsLong())).append('\n');
        help(out, "cloudislands_core_security_rejects_rate_limited_total", "Core API requests rejected by rate limiting");
        type(out, "cloudislands_core_security_rejects_rate_limited_total", "counter");
        out.append("cloudislands_core_security_rejects_rate_limited_total ").append(Math.max(0L, securityRejectsRateLimited.getAsLong())).append('\n');
        help(out, "cloudislands_core_security_rejects_unauthorized_total", "Core API requests rejected because API token authentication failed");
        type(out, "cloudislands_core_security_rejects_unauthorized_total", "counter");
        out.append("cloudislands_core_security_rejects_unauthorized_total ").append(Math.max(0L, securityRejectsUnauthorized.getAsLong())).append('\n');
        help(out, "cloudislands_core_security_rejects_mtls_required_total", "Core API requests rejected because mTLS verification failed");
        type(out, "cloudislands_core_security_rejects_mtls_required_total", "counter");
        out.append("cloudislands_core_security_rejects_mtls_required_total ").append(Math.max(0L, securityRejectsMtlsRequired.getAsLong())).append('\n');
        help(out, "cloudislands_core_security_rejects_ip_not_allowed_total", "Core API requests rejected because the remote address is outside the IP allowlist");
        type(out, "cloudislands_core_security_rejects_ip_not_allowed_total", "counter");
        out.append("cloudislands_core_security_rejects_ip_not_allowed_total ").append(Math.max(0L, securityRejectsIpNotAllowed.getAsLong())).append('\n');
        help(out, "cloudislands_core_security_rejects_admin_permission_denied_total", "Core API requests rejected by admin endpoint permission checks");
        type(out, "cloudislands_core_security_rejects_admin_permission_denied_total", "counter");
        out.append("cloudislands_core_security_rejects_admin_permission_denied_total ").append(Math.max(0L, securityRejectsAdminPermissionDenied.getAsLong())).append('\n');
        Instant now = Instant.now();
        long onlineNodes = 0L;
        long totalPlayers = 0L;
        long totalActiveIslands = 0L;
        long totalActivationQueue = 0L;
        long storageAvailableNodes = 0L;
        long activationEligibleNodes = 0L;
        long staleNodes = 0L;
        long routeCandidateNodes = 0L;
        long routingHealthyNodes = 0L;
        double maxMspt = 0.0D;
        for (NodeLoad node : nodes.snapshot()) {
            long heartbeatAgeSeconds = node.lastHeartbeat() == null ? -1L : Math.max(0L, Duration.between(node.lastHeartbeat(), now).toSeconds());
            boolean fresh = node.lastHeartbeat() != null && Duration.between(node.lastHeartbeat(), now).compareTo(heartbeatTimeout) <= 0;
            boolean stale = !fresh;
            String allocationBlockReason = node.allocationBlockReason(now, heartbeatTimeout);
            boolean routeCandidate = allocationBlockReason.isBlank();
            boolean routingHealthy = fresh && node.state() != NodeState.DOWN && routeCandidate;
            if (stale) {
                staleNodes++;
            }
            if (routeCandidate) {
                routeCandidateNodes++;
            }
            if (routingHealthy) {
                routingHealthyNodes++;
            }
            if (fresh && node.state() != NodeState.DOWN) {
                onlineNodes++;
                totalPlayers += node.players();
                totalActiveIslands += node.activeIslands();
                totalActivationQueue += node.activationQueue();
                if (node.storageAvailable()) {
                    storageAvailableNodes++;
                }
                maxMspt = Math.max(maxMspt, node.mspt());
            }
            labels(out, "cloudislands_nodes_online", node, null).append(fresh && node.state() != NodeState.DOWN ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_heartbeat_age_seconds", node, null).append(heartbeatAgeSeconds).append('\n');
            labels(out, "cloudislands_node_stale", node, null).append(stale ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_route_candidate", node, "reason=\"" + escape(routeCandidate ? "OK" : allocationBlockReason) + "\"").append(routeCandidate ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_routing_healthy", node, "reason=\"" + escape(routingHealthy ? "OK" : allocationBlockReason.isBlank() ? stale ? "STALE_HEARTBEAT" : "NODE_DOWN" : allocationBlockReason) + "\"").append(routingHealthy ? 1 : 0).append('\n');
            labels(out, "cloudislands_node_players", node, null).append(node.players()).append('\n');
            labels(out, "cloudislands_node_soft_player_cap", node, null).append(node.softPlayerCap()).append('\n');
            labels(out, "cloudislands_node_hard_player_cap", node, null).append(node.hardPlayerCap()).append('\n');
            labels(out, "cloudislands_node_reserved_slots", node, null).append(node.reservedSlots()).append('\n');
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
            if (fresh && allocationBlockReason.isBlank()) {
                activationEligibleNodes++;
            }
            labels(out, "cloudislands_node_activation_eligible", node, "reason=\"" + escape(allocationBlockReason.isBlank() ? "OK" : allocationBlockReason) + "\"").append(allocationBlockReason.isBlank() ? 1 : 0).append('\n');
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
            String storageBackend = node.heartbeatMetadata().get("storageBackend");
            if (storageBackend != null && !storageBackend.isBlank()) {
                labels(out, "cloudislands_storage_backend", node, "backend=\"" + escape(storageBackend) + "\"").append(1).append('\n');
            }
            appendMetadataGauge(out, "cloudislands_storage_failures_total", node, "storageHealthCheckFailures", "operation=\"healthcheck\"");
            appendMetadataGauge(out, "cloudislands_storage_failures_total", node, "storageUploadFailures", "operation=\"upload\"");
            appendMetadataGauge(out, "cloudislands_storage_failures_total", node, "storageDownloadFailures", "operation=\"download\"");
            appendMetadataGauge(out, "cloudislands_storage_failures_total", node, "storageOperationFailures", "operation=\"maintenance\"");
            appendMetadataGauge(out, "cloudislands_island_save_seconds", node, "storageUploadSeconds");
            appendMetadataGauge(out, "cloudislands_island_activation_seconds", node, "storageDownloadSeconds");
            appendMetadataGauge(out, "cloudislands_island_snapshot_seconds", node, "storageUploadSeconds");
            appendMetadataGauge(out, "cloudislands_paper_periodic_save_retry_queue", node, "periodicSaveRetryQueue");
            appendMetadataGauge(out, "cloudislands_paper_empty_save_retry_queue", node, "emptySaveRetryQueue");
            appendMetadataGauge(out, "cloudislands_island_save_failures_total", node, "periodicSaveFailures");
            appendMetadataGauge(out, "cloudislands_island_save_failures_total", node, "emptySaveFailures", "source=\"empty\"");
            appendMetadataGauge(out, "cloudislands_paper_periodic_save_failures_total", node, "periodicSaveFailures");
            appendMetadataGauge(out, "cloudislands_paper_empty_save_failures_total", node, "emptySaveFailures");
            appendMetadataGauge(out, "cloudislands_paper_proxy_source_rejections_total", node, "proxySourceRejections");
            appendMetadataGauge(out, "cloudislands_paper_forwarding_rejections_total", node, "forwardingRejections");
            appendMetadataGauge(out, "cloudislands_paper_route_session_rejections_total", node, "routeSessionRejections");
            appendMetadataGauge(out, "cloudislands_paper_route_session_check_failures_total", node, "routeSessionCheckFailures");
            appendMetadataGauge(out, "cloudislands_paper_chat_broadcasts_total", node, "chatBroadcasts");
            appendMetadataGauge(out, "cloudislands_paper_chat_deliveries_total", node, "chatDeliveries");
            appendMetadataGauge(out, "cloudislands_paper_chat_no_recipient_broadcasts_total", node, "chatNoRecipientBroadcasts");
        }
        out.append("cloudislands_cluster_nodes_online ").append(onlineNodes).append('\n');
        out.append("cloudislands_cluster_players ").append(totalPlayers).append('\n');
        out.append("cloudislands_cluster_active_islands ").append(totalActiveIslands).append('\n');
        out.append("cloudislands_cluster_activation_queue ").append(totalActivationQueue).append('\n');
        out.append("cloudislands_cluster_storage_available_nodes ").append(storageAvailableNodes).append('\n');
        out.append("cloudislands_cluster_activation_eligible_nodes ").append(activationEligibleNodes).append('\n');
        out.append("cloudislands_cluster_stale_nodes ").append(staleNodes).append('\n');
        out.append("cloudislands_cluster_route_candidate_nodes ").append(routeCandidateNodes).append('\n');
        out.append("cloudislands_cluster_routing_healthy_nodes ").append(routingHealthyNodes).append('\n');
        out.append("cloudislands_cluster_max_mspt ").append(maxMspt).append('\n');
        help(out, "cloudislands_jobs_total", "Island jobs by in-memory state or backend mode");
        type(out, "cloudislands_jobs_total", "gauge");
        Map<String, Long> jobCounts;
        String jobBackend;
        long jobRetries;
        double redisLatencySeconds = Double.NaN;
        long redisFailures = -1L;
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
            redisFailures = redisJobs.redisFailuresTotal();
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
        help(out, "cloudislands_database_connections_active", "Active JDBC connections currently open in Core API");
        type(out, "cloudislands_database_connections_active", "gauge");
        long activeDatabaseConnections = Math.max(0L, databaseActiveConnections.getAsLong());
        long maxDatabaseConnections = Math.max(0L, databaseMaxConnections.getAsLong());
        out.append("cloudislands_database_connections_active ").append(activeDatabaseConnections).append('\n');
        help(out, "cloudislands_database_connections_max", "Configured maximum JDBC connections for Core API");
        type(out, "cloudislands_database_connections_max", "gauge");
        out.append("cloudislands_database_connections_max ").append(maxDatabaseConnections).append('\n');
        help(out, "cloudislands_database_connection_pool_usage_ratio", "Active JDBC connections divided by configured maximum connections");
        type(out, "cloudislands_database_connection_pool_usage_ratio", "gauge");
        out.append("cloudislands_database_connection_pool_usage_ratio ").append(maxDatabaseConnections <= 0L ? 0.0D : (double) activeDatabaseConnections / maxDatabaseConnections).append('\n');
        help(out, "cloudislands_database_connections_opened_total", "JDBC connections opened by Core API");
        type(out, "cloudislands_database_connections_opened_total", "counter");
        out.append("cloudislands_database_connections_opened_total ").append(databaseOpenedConnections.getAsLong()).append('\n');
        help(out, "cloudislands_database_query_failures_total", "JDBC statement execution failures observed by Core API");
        type(out, "cloudislands_database_query_failures_total", "counter");
        out.append("cloudislands_database_query_failures_total ").append(databaseQueryFailures.getAsLong()).append('\n');
        help(out, "cloudislands_database_connection_failures_total", "JDBC connection acquisition failures observed by Core API");
        type(out, "cloudislands_database_connection_failures_total", "counter");
        out.append("cloudislands_database_connection_failures_total ").append(databaseConnectionFailures.getAsLong()).append('\n');
        if (!Double.isNaN(redisLatencySeconds)) {
            help(out, "cloudislands_redis_latency_seconds", "Redis PING latency observed by Core API");
            type(out, "cloudislands_redis_latency_seconds", "gauge");
            out.append("cloudislands_redis_latency_seconds ").append(redisLatencySeconds).append('\n');
        }
        if (redisFailures >= 0L) {
            help(out, "cloudislands_redis_failures_total", "Redis command failures observed by Core API");
            type(out, "cloudislands_redis_failures_total", "counter");
            out.append("cloudislands_redis_failures_total ").append(redisFailures).append('\n');
        }
        help(out, "cloudislands_redis_event_failures_total", "Redis event stream publish failures observed by Core API");
        type(out, "cloudislands_redis_event_failures_total", "counter");
        out.append("cloudislands_redis_event_failures_total ").append(redisEventFailures.getAsLong()).append('\n');
        help(out, "cloudislands_redis_cache_failures_total", "Redis cache and audit stream failures observed by Core API");
        type(out, "cloudislands_redis_cache_failures_total", "counter");
        out.append("cloudislands_redis_cache_failures_total ").append(redisCacheFailures.getAsLong()).append('\n');
        help(out, "cloudislands_route_ticket_created_total", "Route tickets created by Core API");
        type(out, "cloudislands_route_ticket_created_total", "counter");
        out.append("cloudislands_route_ticket_created_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CREATED.name())).append('\n');
        appendEventFieldCounters(out, "cloudislands_route_ticket_created_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CREATED, "action");
        appendEventFieldCounters(out, "cloudislands_route_ticket_created_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CREATED, "targetNode");
        appendEventFieldCounters(out, "cloudislands_route_ticket_created_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CREATED, "targetServerName");
        appendEventFieldCounters(out, "cloudislands_route_ticket_created_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CREATED, "state");
        help(out, "cloudislands_route_session_published_total", "Route sessions published by Velocity or Paper before backend connection");
        type(out, "cloudislands_route_session_published_total", "counter");
        out.append("cloudislands_route_session_published_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_SESSION_PUBLISHED.name())).append('\n');
        appendEventFieldCounters(out, "cloudislands_route_session_published_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_SESSION_PUBLISHED, "action");
        appendEventFieldCounters(out, "cloudislands_route_session_published_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_SESSION_PUBLISHED, "targetNode");
        help(out, "cloudislands_route_ticket_consumed_total", "Route tickets consumed by Paper nodes");
        type(out, "cloudislands_route_ticket_consumed_total", "counter");
        out.append("cloudislands_route_ticket_consumed_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CONSUMED.name())).append('\n');
        appendEventFieldCounters(out, "cloudislands_route_ticket_consumed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CONSUMED, "action");
        appendEventFieldCounters(out, "cloudislands_route_ticket_consumed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CONSUMED, "targetNode");
        publishExpiredRouteTickets();
        help(out, "cloudislands_route_ticket_failed_total", "Route ticket failures recorded by Core API");
        type(out, "cloudislands_route_ticket_failed_total", "counter");
        out.append("cloudislands_route_ticket_failed_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED.name())).append('\n');
        appendEventFieldCounters(out, "cloudislands_route_ticket_failed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED, "action");
        appendEventFieldCounters(out, "cloudislands_route_ticket_failed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED, "targetNode");
        for (Map.Entry<String, Long> entry : events.countsByField(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED.name(), "reason").entrySet()) {
            out.append("cloudislands_route_ticket_failed_total{reason=\"").append(escape(entry.getKey())).append("\"} ").append(entry.getValue()).append('\n');
        }
        help(out, "cloudislands_route_ticket_expired_total", "Route tickets that expired before being consumed");
        type(out, "cloudislands_route_ticket_expired_total", "counter");
        out.append("cloudislands_route_ticket_expired_total ").append(events.countsByField(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED.name(), "reason").getOrDefault("TICKET_EXPIRED", 0L)).append('\n');
        help(out, "cloudislands_route_ticket_cleared_total", "Route tickets or sessions cleared by admin or failed connection cleanup");
        type(out, "cloudislands_route_ticket_cleared_total", "counter");
        out.append("cloudislands_route_ticket_cleared_total ").append(events.countByType(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CLEARED.name())).append('\n');
        appendEventFieldCounters(out, "cloudislands_route_ticket_cleared_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_CLEARED, "reason");
        help(out, "cloudislands_route_tickets_total", "Route tickets currently stored by state");
        type(out, "cloudislands_route_tickets_total", "gauge");
        for (Map.Entry<String, Long> entry : tickets.countsByState().entrySet()) {
            out.append("cloudislands_route_tickets_total{state=\"").append(escape(entry.getKey())).append("\"} ").append(entry.getValue()).append('\n');
        }
        help(out, "cloudislands_island_runtimes_total", "Island runtimes currently stored by lifecycle state");
        type(out, "cloudislands_island_runtimes_total", "gauge");
        for (Map.Entry<String, Long> entry : runtimes.countsByState().entrySet()) {
            out.append("cloudislands_island_runtimes_total{state=\"").append(escape(entry.getKey())).append("\"} ").append(entry.getValue()).append('\n');
        }
        eventCounter(out, "cloudislands_island_activation_requested_total", "Island activation requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ACTIVATE_REQUESTED);
        eventCounter(out, "cloudislands_island_created_total", "Islands created by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_CREATED);
        eventCounter(out, "cloudislands_island_activated_total", "Island activations completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ACTIVATED);
        eventCounter(out, "cloudislands_island_deactivated_total", "Island deactivations completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_DEACTIVATED);
        eventCounter(out, "cloudislands_island_delete_requested_total", "Island delete requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_DELETE_REQUESTED);
        eventCounter(out, "cloudislands_island_deleted_total", "Island deletions completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_DELETED);
        eventCounter(out, "cloudislands_island_reset_requested_total", "Island reset requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RESET_REQUESTED);
        eventCounter(out, "cloudislands_island_reset_total", "Island resets completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RESET);
        eventCounter(out, "cloudislands_island_restore_requested_total", "Island restore requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RESTORE_REQUESTED);
        eventCounter(out, "cloudislands_island_restored_total", "Island restores completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RESTORED);
        eventCounter(out, "cloudislands_island_snapshot_requested_total", "Island snapshot requests accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_SNAPSHOT_REQUESTED);
        eventCounter(out, "cloudislands_island_snapshot_created_total", "Island snapshots completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_SNAPSHOT_CREATED);
        eventCounter(out, "cloudislands_island_migrated_total", "Island migrations completed by node workers", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_MIGRATED);
        eventCounter(out, "cloudislands_island_recovery_required_total", "Islands marked for recovery after node failure", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_RECOVERY_REQUIRED);
        eventCounter(out, "cloudislands_island_level_updated_total", "Island level recalculations completed by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_LEVEL_UPDATED);
        eventCounter(out, "cloudislands_island_worth_changed_total", "Island worth recalculations completed by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_WORTH_CHANGED);
        eventCounter(out, "cloudislands_island_block_value_changed_total", "Island block value rules changed through Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BLOCK_VALUE_CHANGED);
        help(out, "cloudislands_ranking_dirty_drained_total", "Dirty island rankings drained by the Core recalculation worker");
        type(out, "cloudislands_ranking_dirty_drained_total", "counter");
        out.append("cloudislands_ranking_dirty_drained_total ").append(rankingDirtyDrainedTotal.getAsLong()).append('\n');
        help(out, "cloudislands_ranking_recalculated_total", "Dirty island rankings successfully recalculated by the Core worker");
        type(out, "cloudislands_ranking_recalculated_total", "counter");
        out.append("cloudislands_ranking_recalculated_total ").append(rankingRecalculatedTotal.getAsLong()).append('\n');
        help(out, "cloudislands_ranking_recalculation_failures_total", "Dirty island ranking recalculation failures requeued by the Core worker");
        type(out, "cloudislands_ranking_recalculation_failures_total", "counter");
        out.append("cloudislands_ranking_recalculation_failures_total ").append(rankingRecalculationFailuresTotal.getAsLong()).append('\n');
        help(out, "cloudislands_ranking_recalculation_last_batch_size", "Dirty island rankings drained in the last Core worker batch");
        type(out, "cloudislands_ranking_recalculation_last_batch_size", "gauge");
        out.append("cloudislands_ranking_recalculation_last_batch_size ").append(rankingRecalculationLastBatchSize.getAsLong()).append('\n');
        eventCounter(out, "cloudislands_island_blocks_changed_total", "Island block delta updates received by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BLOCKS_CHANGED);
        eventCounter(out, "cloudislands_island_bank_changed_total", "Island bank balance changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BANK_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_bank_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BANK_CHANGED, "operation");
        eventCounter(out, "cloudislands_island_upgrade_total", "Island upgrades purchased through Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_UPGRADE);
        appendEventFieldCounters(out, "cloudislands_island_upgrade_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_UPGRADE, "upgradeKey");
        eventCounter(out, "cloudislands_island_limit_changed_total", "Island limit changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_LIMIT_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_limit_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_LIMIT_CHANGED, "limitKey");
        eventCounter(out, "cloudislands_island_mission_completed_total", "Island missions completed through Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_MISSION_COMPLETED);
        appendEventFieldCounters(out, "cloudislands_island_mission_completed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_MISSION_COMPLETED, "kind");
        eventCounter(out, "cloudislands_island_invite_changed_total", "Island invite changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_INVITE_CHANGED);
        eventCounter(out, "cloudislands_island_member_changed_total", "Island member changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_MEMBER_CHANGED);
        eventCounter(out, "cloudislands_island_ownership_changed_total", "Island ownership changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_OWNERSHIP_CHANGED);
        eventCounter(out, "cloudislands_island_access_changed_total", "Island public access changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ACCESS_CHANGED);
        eventCounter(out, "cloudislands_island_visitor_ban_changed_total", "Island visitor ban changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED);
        eventCounter(out, "cloudislands_island_flag_changed_total", "Island flag changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_FLAG_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_flag_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_FLAG_CHANGED, "flag");
        eventCounter(out, "cloudislands_island_permission_changed_total", "Island permission changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_PERMISSION_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_permission_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_PERMISSION_CHANGED, "permission");
        eventCounter(out, "cloudislands_island_role_changed_total", "Island role catalog changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ROLE_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_role_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ROLE_CHANGED, "operation");
        appendEventFieldCounters(out, "cloudislands_island_role_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_ROLE_CHANGED, "role");
        eventCounter(out, "cloudislands_island_biome_changed_total", "Island biome changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BIOME_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_biome_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_BIOME_CHANGED, "biomeKey");
        eventCounter(out, "cloudislands_island_home_changed_total", "Island home changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_HOME_CHANGED);
        eventCounter(out, "cloudislands_island_warp_changed_total", "Island warp changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_WARP_CHANGED);
        appendEventFieldCounters(out, "cloudislands_island_warp_changed_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_WARP_CHANGED, "operation");
        eventCounter(out, "cloudislands_island_chat_sent_total", "Island chat messages accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_CHAT_SENT);
        appendEventFieldCounters(out, "cloudislands_island_chat_sent_total", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_CHAT_SENT, "channel");
        eventCounter(out, "cloudislands_island_template_changed_total", "Island template changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.ISLAND_TEMPLATE_CHANGED);
        eventCounter(out, "cloudislands_node_state_changed_total", "Node state changes accepted by Core API", kr.lunaf.cloudislands.common.event.CloudIslandEventType.NODE_STATE_CHANGED);
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

    private void appendEventFieldCounters(StringBuilder out, String name, kr.lunaf.cloudislands.common.event.CloudIslandEventType eventType, String fieldName) {
        for (Map.Entry<String, Long> entry : events.countsByField(eventType.name(), fieldName).entrySet()) {
            if (!entry.getKey().isBlank()) {
                out.append(name).append('{').append(fieldName).append("=\"").append(escape(entry.getKey())).append("\"} ").append(entry.getValue()).append('\n');
            }
        }
    }

    private void publishExpiredRouteTickets() {
        for (var ticket : tickets.expireStale()) {
            events.publish(kr.lunaf.cloudislands.common.event.CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                "ticketId", ticket.ticketId().toString(),
                "playerUuid", ticket.playerUuid().toString(),
                "islandId", ticket.islandId().toString(),
                "action", ticket.action().name(),
                "targetNode", ticket.targetNode(),
                "reason", "TICKET_EXPIRED"
            ));
        }
    }

    private void appendMetadataGauge(StringBuilder out, String name, NodeLoad node, String metadataKey) {
        appendMetadataGauge(out, name, node, metadataKey, null);
    }

    private void appendMetadataGauge(StringBuilder out, String name, NodeLoad node, String metadataKey, String extraLabel) {
        String value = node.heartbeatMetadata().get(metadataKey);
        if (value != null && !value.isBlank()) {
            labels(out, name, node, extraLabel).append(value).append('\n');
        }
    }

    private static double memoryPressure(NodeLoad node) {
        return node.heapMaxMb() <= 0 ? 1.0D : Math.min((double) node.heapUsedMb() / node.heapMaxMb(), 1.5D);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
