package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.paper.activation.EmptyIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.PeriodicIslandSaveTask;
import kr.lunaf.cloudislands.paper.cache.PermissionEventPoller;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.generator.IslandGeneratorListener;
import kr.lunaf.cloudislands.paper.level.PeriodicIslandLevelScanTask;
import kr.lunaf.cloudislands.paper.redis.PaperRedisClient;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import kr.lunaf.cloudislands.paper.storage.MeteredIslandStorage;

final class PaperObservabilityFormatter {
    private final CloudIslandsPaperPlugin plugin;
    private final PaperRuntimeConfig config;

    PaperObservabilityFormatter(CloudIslandsPaperPlugin plugin, PaperRuntimeConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    String healthJson(AgentRole role, String nodeId) {
        PaperRedisClient.PingResult redis = plugin.redisClient() == null ? PaperRedisClient.PingResult.disabled() : plugin.redisClient().ping();
        boolean forwardingRequired = config.security().requireVelocityForwarding();
        boolean forwardingSecretConfigured = !config.security().forwardingSecret().isBlank();
        boolean routeSessionEnforced = config.security().enforceRouteSession() || config.security().requireRouteSession();
        boolean hideNodeNames = config.routing().hideNodeNames();
        boolean topologyExposureRisk = !hideNodeNames;
        boolean defaultNodeIdentityRisk = plugin.defaultNodeIdentityRisk(role, nodeId, config.node().velocityServerName());
        int proxySourceAllowlistEntries = plugin.proxySourceAllowlist() == null ? 0 : plugin.proxySourceAllowlist().entryCount();
        boolean proxySourceAllowlistConfigured = proxySourceAllowlistEntries > 0;
        boolean proxySourceAllowlistRequired = role == AgentRole.ISLAND_NODE && config.security().requireProxySourceAllowlist();
        boolean directAccessRisk = role == AgentRole.ISLAND_NODE && !plugin.getServer().getOnlineMode() && !proxySourceAllowlistConfigured && !proxySourceAllowlistRequired;
        boolean velocityOnlineModeMismatch = role == AgentRole.ISLAND_NODE && forwardingRequired && plugin.getServer().getOnlineMode();
        boolean bungeeConnectPluginMessaging = config.security().allowBungeeConnectPluginMessaging();
        boolean bungeeConnectChannelRegistered = plugin.getServer().getMessenger().isOutgoingChannelRegistered(plugin, "BungeeCord");
        boolean islandNodeRole = role == AgentRole.ISLAND_NODE;
        boolean worldExecutionEnabled = islandNodeRole && plugin.activeIslands() != null;
        boolean activationWorkerEnabled = islandNodeRole && plugin.jobWorker() != null;
        boolean islandProtectionEnabled = islandNodeRole;
        boolean islandSaveTasksEnabled = islandNodeRole && (plugin.periodicSaveTask() != null || plugin.emptyIslandSaveTask() != null);
        boolean guiMenusEnabled = plugin.guiEnabledForRole(role);
        PaperRouteSessionListener routeSessions = plugin.routeSessionListener();
        RouteTicketConsumer routeTickets = plugin.agent() == null ? null : plugin.agent().routeTickets();
        PermissionEventPoller events = plugin.permissionEventPoller();
        PeriodicIslandSaveTask saver = plugin.periodicSaveTask();
        EmptyIslandSaveTask emptySaver = plugin.emptyIslandSaveTask();
        ProtectionController protection = plugin.agent() == null ? null : plugin.agent().protection();
        IslandBoundaryListener boundary = plugin.boundaryListener();
        MeteredIslandStorage storage = plugin.islandStorage();
        boolean storageFallbackEnabled = config.storage().fallbackEnabled();
        int storageSaveRetryQueueTotal = (saver == null ? 0 : saver.retryQueueSize()) + (emptySaver == null ? 0 : emptySaver.retryQueueSize());
        String storageLastFallbackReason = storage == null ? "" : storage.lastFallbackReason();
        return "{"
            + "\"status\":\"UP\","
            + "\"role\":\"" + role.name() + "\","
            + "\"nodeId\":\"" + nodeId + "\","
            + "\"onlineMode\":" + plugin.getServer().getOnlineMode() + ","
            + "\"onlinePlayers\":" + plugin.getServer().getOnlinePlayers().size() + ","
            + "\"rolePolicy\":\"" + (islandNodeRole ? "island-node-runs-worlds-protection-routing-and-saves" : "lobby-runs-gui-query-and-routing-entry-only") + "\","
            + "\"roleWorldExecutionEnabled\":" + worldExecutionEnabled + ","
            + "\"roleActivationWorkerEnabled\":" + activationWorkerEnabled + ","
            + "\"roleIslandProtectionEnabled\":" + islandProtectionEnabled + ","
            + "\"roleIslandSaveTasksEnabled\":" + islandSaveTasksEnabled + ","
            + "\"roleGuiMenusEnabled\":" + guiMenusEnabled + ","
            + "\"roleGuiMenuPolicy\":\"configurable-paper-gui-role-gate\","
            + "\"protectionDecisionPolicy\":\"" + (protection == null ? "unavailable" : protection.synchronousDecisionPolicy()) + "\","
            + "\"protectionSynchronousEventsUseRemoteCalls\":false,"
            + "\"protectionIndexedChunks\":" + (protection == null ? 0 : protection.indexedChunkCount()) + ","
            + "\"protectionIndexedIslands\":" + (protection == null ? 0 : protection.indexedIslandCount()) + ","
            + "\"protectionMigratingIslands\":" + (protection == null ? 0 : protection.migratingIslandCount()) + ","
            + "\"boundaryMemberReturnsTotal\":" + (boundary == null ? 0L : boundary.memberReturns()) + ","
            + "\"boundaryVisitorReturnsTotal\":" + (boundary == null ? 0L : boundary.visitorReturns()) + ","
            + "\"boundaryAdminBypassesTotal\":" + (boundary == null ? 0L : boundary.adminBypasses()) + ","
            + "\"activeIslands\":" + (plugin.activeIslands() == null ? 0 : plugin.activeIslands().size()) + ","
            + "\"activationQueue\":" + (plugin.jobWorker() == null ? 0 : plugin.jobWorker().activationQueue()) + ","
            + "\"storageBackend\":\"" + (storage == null ? "NONE" : storage.backend()) + "\","
            + "\"storagePrimaryDegraded\":" + (storage != null && storage.primaryStorageDegraded()) + ","
            + "\"storagePrimaryFailuresTotal\":" + (storage == null ? 0L : storage.primaryStorageFailures()) + ","
            + "\"storageFallbackReadsTotal\":" + (storage == null ? 0L : storage.fallbackReads()) + ","
            + "\"storageFallbackWritesTotal\":" + (storage == null ? 0L : storage.fallbackWrites()) + ","
            + "\"storageFallbackDeletesTotal\":" + (storage == null ? 0L : storage.fallbackDeletes()) + ","
            + "\"storageFallbackOperationsTotal\":" + (storage == null ? 0L : storage.fallbackOperations()) + ","
            + "\"storageFallbackEnabled\":" + storageFallbackEnabled + ","
            + "\"storageFallbackType\":\"" + jsonText(config.storage().fallback().backend()) + "\","
            + "\"storageLastFallbackReason\":\"" + jsonText(storageLastFallbackReason) + "\","
            + "\"storageSaveRetryQueueTotal\":" + storageSaveRetryQueueTotal + ","
            + "\"integrationsDetected\":\"" + jsonText(integrationList(true)) + "\","
            + "\"integrationsMissing\":\"" + jsonText(integrationList(false)) + "\","
            + "\"integrationPolicy\":\"" + kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY + "\","
            + "\"objectStorageOutagePolicy\":\"" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.CONTRACT + "\","
            + "\"objectStorageActiveIslandPolicy\":\"" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.ACTIVE_ISLAND_POLICY + "\","
            + "\"objectStorageNewActivationPolicy\":\"" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.NEW_ACTIVATION_POLICY + "\","
            + "\"objectStorageActiveIslandLocalPlayAllowed\":" + (islandNodeRole && plugin.activeIslands() != null) + ","
            + "\"objectStorageSaveRetryPolicy\":\"" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.SAVE_RETRY_POLICY + "\","
            + "\"objectStorageDeactivationPolicy\":\"" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.DEACTIVATION_POLICY + "\","
            + "\"objectStorageObservabilityKeys\":\"" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.OBSERVABILITY_KEYS + "\","
            + "\"islandBundlePortablePolicy\":\"portable-node-agnostic-shard-cell-remap\","
            + "\"islandBundleManifestPolicy\":\"manifest-json-plus-checksums-sha256-sidecar\","
            + "\"islandBundleRestoreVerifyPolicy\":\"verify-manifest-checksum-for-latest-snapshot-and-storage-path-when-present\","
            + "\"islandBundlePlacementPolicy\":\"restore-to-current-active-node-world-and-cell\","
            + "\"finalRequestFlowKeys\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowKeys() + "\","
            + "\"finalRequestFlowIslandCreate\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("island-create") + "\","
            + "\"finalRequestFlowIslandHome\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("island-home") + "\","
            + "\"finalRequestFlowIslandVisit\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("island-visit") + "\","
            + "\"finalRequestFlowSoftFullRouting\":\"" + kr.lunaf.cloudislands.common.routing.FinalRequestFlowPolicy.flowSummary("soft-full-routing") + "\","
            + "\"storageUploadFailuresTotal\":" + (storage == null ? 0L : storage.uploadFailures()) + ","
            + "\"storageDownloadFailuresTotal\":" + (storage == null ? 0L : storage.downloadFailures()) + ","
            + "\"storageOperationFailuresTotal\":" + (storage == null ? 0L : storage.operationFailures()) + ","
            + "\"redisAvailable\":" + redis.available() + ","
            + "\"redisLatencySeconds\":" + redis.latencySeconds() + ","
            + "\"redisFailuresTotal\":" + redis.failuresTotal() + ","
            + "\"backendAccessPolicy\":\"" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.CONTRACT + "\","
            + "\"velocityForwardingModePolicy\":\"" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.MODERN_FORWARDING_POLICY + "\","
            + "\"velocityForwardingRequired\":" + forwardingRequired + ","
            + "\"forwardingSecretConfigured\":" + forwardingSecretConfigured + ","
            + "\"forwardingSecretPolicy\":\"" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.FORWARDING_SECRET_POLICY + "\","
            + "\"paperDirectAccessPolicy\":\"" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.PAPER_DIRECT_ACCESS_POLICY + "\","
            + "\"paperOnlineModePolicy\":\"" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.PAPER_ONLINE_MODE_POLICY + "\","
            + "\"backendFirewallPolicy\":\"" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.INFRASTRUCTURE_EXPOSURE_POLICY + "\","
            + "\"routeSessionEnforced\":" + routeSessionEnforced + ","
            + "\"routeTicketOneTimeConsume\":" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.ONE_TIME_CONSUME + ","
            + "\"routeTicketNonceRequired\":" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.NONCE_REQUIRED + ","
            + "\"routeTicketTargetNodeRequired\":" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.TARGET_NODE_REQUIRED + ","
            + "\"routeTicketOneTimePolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.ONE_TIME_POLICY + "\","
            + "\"routeTicketNoncePolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.NONCE_POLICY + "\","
            + "\"routeTicketArrivalConsumePolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.ARRIVAL_CONSUME_POLICY + "\","
            + "\"routeTicketDirectAccessPolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.DIRECT_ACCESS_POLICY + "\","
            + "\"routeTicketReplayPolicy\":\"" + kr.lunaf.cloudislands.protocol.route.RouteTicketPolicy.REPLAY_POLICY + "\","
            + "\"routeTicketConsumeSequence\":\"prelogin-session-check>playerjoin-session-consume>route-ticket-consume>teleport\","
            + "\"hideNodeNames\":" + hideNodeNames + ","
            + "\"playerTopologyPolicy\":\"logical-island-only\","
            + "\"playerNodeNamePolicy\":\"" + (hideNodeNames ? "hidden-from-player-routing-messages" : "visible-risk-admin-debug-only") + "\","
            + "\"playerRouteMessagePolicy\":\"no-physical-server-node-world-or-cell-names\","
            + "\"adminTopologyDiagnosticsPolicy\":\"admin-status-and-events-only-not-player-chat\","
            + "\"topologyExposureRisk\":" + topologyExposureRisk + ","
            + "\"defaultNodeIdentityRisk\":" + defaultNodeIdentityRisk + ","
            + "\"proxySourceAllowlistRequired\":" + proxySourceAllowlistRequired + ","
            + "\"proxySourceAllowlistConfigured\":" + proxySourceAllowlistConfigured + ","
            + "\"proxySourceAllowlistEntries\":" + proxySourceAllowlistEntries + ","
            + "\"directAccessRisk\":" + directAccessRisk + ","
            + "\"velocityOnlineModeMismatch\":" + velocityOnlineModeMismatch + ","
            + "\"pluginMessagingControlPolicy\":\"forbidden-for-core-control\","
            + "\"bungeeConnectPluginMessagingEnabled\":" + bungeeConnectPluginMessaging + ","
            + "\"bungeeConnectChannelRegistered\":" + bungeeConnectChannelRegistered + ","
            + "\"bungeeConnectUseCase\":\"fallback-transfer-only\","
            + "\"proxySourceConfigurationRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.proxySourceConfigurationRejections()) + ","
            + "\"proxySourceRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.proxySourceRejections()) + ","
            + "\"forwardingRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.forwardingRejections()) + ","
            + "\"routeSessionRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.routeSessionRejections()) + ","
            + "\"routeSessionCheckFailuresTotal\":" + (routeSessions == null ? 0L : routeSessions.routeSessionCheckFailures()) + ","
            + "\"routeTicketConsumeRetriesTotal\":" + (routeTickets == null ? 0L : routeTickets.consumeRetries()) + ","
            + "\"routeTicketConsumeFailuresTotal\":" + (routeTickets == null ? 0L : routeTickets.consumeFailures()) + ","
            + "\"routeTicketWorldWaitRetriesTotal\":" + (routeTickets == null ? 0L : routeTickets.worldWaitRetries()) + ","
            + "\"routeTicketTeleportAttemptsTotal\":" + (routeTickets == null ? 0L : routeTickets.teleportAttempts()) + ","
            + "\"routeTicketTeleportSuccessesTotal\":" + (routeTickets == null ? 0L : routeTickets.teleportSuccesses()) + ","
            + "\"routeTicketTeleportFailuresTotal\":" + (routeTickets == null ? 0L : routeTickets.teleportFailures()) + ","
            + "\"routeTicketLastFailureReason\":\"" + jsonText(routeTickets == null ? "" : routeTickets.lastFailureReason()) + "\","
            + "\"routeTicketLastTargetType\":\"" + jsonText(routeTickets == null ? "" : routeTickets.lastTargetType()) + "\","
            + "\"routeTicketTeleportPolicy\":\"consume-ready-ticket-then-teleport-active-island-origin-plus-local-offset\","
            + "\"chatBroadcastsTotal\":" + (events == null ? 0L : events.chatBroadcasts()) + ","
            + "\"chatDeliveriesTotal\":" + (events == null ? 0L : events.chatDeliveries()) + ","
            + "\"chatNoRecipientBroadcastsTotal\":" + (events == null ? 0L : events.chatNoRecipientBroadcasts()) + ","
            + "\"islandMutationEvacuationsTotal\":" + (events == null ? 0L : events.islandMutationEvacuations()) + ","
            + "\"islandMutationFallbackTransfersTotal\":" + (events == null ? 0L : events.islandMutationFallbackTransfers()) + ","
            + "\"islandMutationFallbackKicksTotal\":" + (events == null ? 0L : events.islandMutationFallbackKicks()) + ","
            + "\"islandMutationFallbackFailuresTotal\":" + (events == null ? 0L : events.islandMutationFallbackFailures()) + ","
            + "\"cacheEventInvalidationsTotal\":" + (events == null ? 0L : events.cacheEventInvalidations()) + ","
            + "\"cacheGapInvalidationsTotal\":" + (events == null ? 0L : events.cacheGapInvalidations()) + ","
            + "\"periodicSaveRetryQueue\":" + (saver == null ? 0 : saver.retryQueueSize()) + ","
            + "\"periodicSaveFailuresTotal\":" + (saver == null ? 0L : saver.failuresTotal()) + ","
            + "\"emptySaveRetryQueue\":" + (emptySaver == null ? 0 : emptySaver.retryQueueSize()) + ","
            + "\"emptySaveFailuresTotal\":" + (emptySaver == null ? 0L : emptySaver.failuresTotal()) + ","
            + "\"localCacheCount\":" + (plugin.localCaches() == null ? 0 : plugin.localCaches().cacheCount()) + ","
            + "\"localCacheInvalidationsTotal\":" + (plugin.localCaches() == null ? 0 : plugin.localCaches().invalidationsTotal())
            + "}";
    }

    String metricsText(AgentRole role, String nodeId, MeteredIslandStorage storage) {
        int active = plugin.activeIslands() == null ? 0 : plugin.activeIslands().size();
        int queue = plugin.jobWorker() == null ? 0 : plugin.jobWorker().activationQueue();
        int failures = plugin.jobWorker() == null ? 0 : plugin.jobWorker().recentFailurePenalty();
        PaperRedisClient.PingResult redis = plugin.redisClient() == null ? PaperRedisClient.PingResult.disabled() : plugin.redisClient().ping();
        double storageUploadSeconds = storage == null ? 0.0D : storage.lastUploadSeconds();
        double storageDownloadSeconds = storage == null ? 0.0D : storage.lastDownloadSeconds();
        long storageFailures = storage == null ? 0L : storage.operationFailures();
        long storageAttempts = storage == null ? 0L : storage.operationAttempts();
        double storageFailureRatio = storage == null ? 0.0D : storage.operationFailureRatio();
        String storageBackend = storage == null ? "NONE" : storage.backend();
        boolean primaryStorageDegraded = storage != null && storage.primaryStorageDegraded();
        long primaryStorageFailures = storage == null ? 0L : storage.primaryStorageFailures();
        long storageFallbackReads = storage == null ? 0L : storage.fallbackReads();
        long storageFallbackWrites = storage == null ? 0L : storage.fallbackWrites();
        long storageFallbackDeletes = storage == null ? 0L : storage.fallbackDeletes();
        long storageFallbackOperations = storage == null ? 0L : storage.fallbackOperations();
        boolean forwardingRequired = config.security().requireVelocityForwarding();
        boolean forwardingSecretConfigured = !config.security().forwardingSecret().isBlank();
        boolean routeSessionEnforced = config.security().enforceRouteSession() || config.security().requireRouteSession();
        boolean hideNodeNames = config.routing().hideNodeNames();
        boolean topologyExposureRisk = !hideNodeNames;
        boolean defaultNodeIdentityRisk = plugin.defaultNodeIdentityRisk(role, nodeId, config.node().velocityServerName());
        int proxySourceAllowlistEntries = plugin.proxySourceAllowlist() == null ? 0 : plugin.proxySourceAllowlist().entryCount();
        boolean proxySourceAllowlistConfigured = proxySourceAllowlistEntries > 0;
        boolean proxySourceAllowlistRequired = role == AgentRole.ISLAND_NODE && config.security().requireProxySourceAllowlist();
        boolean directAccessRisk = role == AgentRole.ISLAND_NODE && !plugin.getServer().getOnlineMode() && !proxySourceAllowlistConfigured && !proxySourceAllowlistRequired;
        boolean velocityOnlineModeMismatch = role == AgentRole.ISLAND_NODE && forwardingRequired && plugin.getServer().getOnlineMode();
        boolean bungeeConnectPluginMessaging = config.security().allowBungeeConnectPluginMessaging();
        boolean guiMenusEnabled = plugin.guiEnabledForRole(role);
        boolean storageFallbackEnabled = config.storage().fallbackEnabled();
        PeriodicIslandLevelScanTask scanner = plugin.periodicLevelScanTask();
        PeriodicIslandSaveTask saver = plugin.periodicSaveTask();
        EmptyIslandSaveTask emptySaver = plugin.emptyIslandSaveTask();
        int storageSaveRetryQueueTotal = (saver == null ? 0 : saver.retryQueueSize()) + (emptySaver == null ? 0 : emptySaver.retryQueueSize());
        IslandGeneratorListener generator = plugin.generatorListener();
        PaperRouteSessionListener routeSessions = plugin.routeSessionListener();
        RouteTicketConsumer routeTickets = plugin.agent().routeTickets();
        PermissionEventPoller events = plugin.permissionEventPoller();
        ProtectionController protection = plugin.agent().protection();
        IslandBoundaryListener boundary = plugin.boundaryListener();
        return ""
            + "cloudislands_paper_online_players " + plugin.getServer().getOnlinePlayers().size() + "\n"
            + "cloudislands_paper_online_mode " + (plugin.getServer().getOnlineMode() ? 1 : 0) + "\n"
            + "cloudislands_paper_gui_menus_enabled{node=\"" + nodeId + "\",role=\"" + role.name() + "\"} " + (guiMenusEnabled ? 1 : 0) + "\n"
            + "cloudislands_paper_active_islands{node=\"" + nodeId + "\",role=\"" + role.name() + "\"} " + active + "\n"
            + "cloudislands_paper_activation_queue{node=\"" + nodeId + "\"} " + queue + "\n"
            + "cloudislands_paper_recent_failure_penalty{node=\"" + nodeId + "\"} " + failures + "\n"
            + "cloudislands_storage_upload_seconds{node=\"" + nodeId + "\"} " + storageUploadSeconds + "\n"
            + "cloudislands_storage_download_seconds{node=\"" + nodeId + "\"} " + storageDownloadSeconds + "\n"
            + "cloudislands_storage_operations_total{node=\"" + nodeId + "\"} " + storageAttempts + "\n"
            + "cloudislands_storage_operation_failures_total{node=\"" + nodeId + "\"} " + storageFailures + "\n"
            + "cloudislands_storage_failure_ratio{node=\"" + nodeId + "\"} " + storageFailureRatio + "\n"
            + "cloudislands_storage_backend{node=\"" + nodeId + "\",backend=\"" + storageBackend + "\"} " + (storage == null ? 0 : 1) + "\n"
            + "cloudislands_storage_primary_degraded{node=\"" + nodeId + "\"} " + (primaryStorageDegraded ? 1 : 0) + "\n"
            + "cloudislands_storage_primary_failures_total{node=\"" + nodeId + "\"} " + primaryStorageFailures + "\n"
            + "cloudislands_storage_fallback_reads_total{node=\"" + nodeId + "\"} " + storageFallbackReads + "\n"
            + "cloudislands_storage_fallback_writes_total{node=\"" + nodeId + "\"} " + storageFallbackWrites + "\n"
            + "cloudislands_storage_fallback_deletes_total{node=\"" + nodeId + "\"} " + storageFallbackDeletes + "\n"
            + "cloudislands_storage_fallback_operations_total{node=\"" + nodeId + "\"} " + storageFallbackOperations + "\n"
            + "cloudislands_storage_fallback_enabled{node=\"" + nodeId + "\"} " + (storageFallbackEnabled ? 1 : 0) + "\n"
            + "cloudislands_storage_save_retry_queue{node=\"" + nodeId + "\"} " + storageSaveRetryQueueTotal + "\n"
            + "cloudislands_storage_active_island_local_play_allowed_during_outage{node=\"" + nodeId + "\"} " + (role == AgentRole.ISLAND_NODE && plugin.activeIslands() != null ? 1 : 0) + "\n"
            + "cloudislands_island_bundle_portable_policy{node=\"" + nodeId + "\",policy=\"portable_node_agnostic_shard_cell_remap\"} 1\n"
            + "cloudislands_island_bundle_manifest_policy{node=\"" + nodeId + "\",policy=\"manifest_json_plus_checksums_sha256_sidecar\"} 1\n"
            + "cloudislands_island_bundle_restore_verify_policy{node=\"" + nodeId + "\",policy=\"verify_manifest_checksum_for_latest_snapshot_and_storage_path_when_present\"} 1\n"
            + "cloudislands_island_save_seconds{node=\"" + nodeId + "\"} " + storageUploadSeconds + "\n"
            + "cloudislands_island_save_failures_total{node=\"" + nodeId + "\"} " + ((saver == null ? 0L : saver.failuresTotal()) + (emptySaver == null ? 0L : emptySaver.failuresTotal())) + "\n"
            + "cloudislands_island_activation_seconds{node=\"" + nodeId + "\"} " + storageDownloadSeconds + "\n"
            + "cloudislands_island_snapshot_seconds{node=\"" + nodeId + "\"} " + storageUploadSeconds + "\n"
            + "cloudislands_paper_periodic_save_retry_queue{node=\"" + nodeId + "\"} " + (saver == null ? 0 : saver.retryQueueSize()) + "\n"
            + "cloudislands_paper_periodic_save_failures_total{node=\"" + nodeId + "\"} " + (saver == null ? 0L : saver.failuresTotal()) + "\n"
            + "cloudislands_paper_empty_save_retry_queue{node=\"" + nodeId + "\"} " + (emptySaver == null ? 0 : emptySaver.retryQueueSize()) + "\n"
            + "cloudislands_paper_empty_save_failures_total{node=\"" + nodeId + "\"} " + (emptySaver == null ? 0L : emptySaver.failuresTotal()) + "\n"
            + "cloudislands_paper_level_scan_running{node=\"" + nodeId + "\"} " + (scanner != null && scanner.running() ? 1 : 0) + "\n"
            + "cloudislands_paper_level_scan_last_started_at{node=\"" + nodeId + "\"} " + (scanner == null ? 0L : scanner.lastScanStartedAt()) + "\n"
            + "cloudislands_paper_level_scan_last_finished_at{node=\"" + nodeId + "\"} " + (scanner == null ? 0L : scanner.lastScanFinishedAt()) + "\n"
            + "cloudislands_paper_level_scan_last_failed_at{node=\"" + nodeId + "\"} " + (scanner == null ? 0L : scanner.lastScanFailedAt()) + "\n"
            + "cloudislands_paper_generator_events_total{node=\"" + nodeId + "\",source=\"block_form\"} " + (generator == null ? 0L : generator.formEvents()) + "\n"
            + "cloudislands_paper_generator_events_total{node=\"" + nodeId + "\",source=\"fluid_collision\"} " + (generator == null ? 0L : generator.fluidCollisionEvents()) + "\n"
            + "cloudislands_paper_generator_replacements_total{node=\"" + nodeId + "\",source=\"block_form\"} " + (generator == null ? 0L : generator.formReplacements()) + "\n"
            + "cloudislands_paper_generator_replacements_total{node=\"" + nodeId + "\",source=\"fluid_collision\"} " + (generator == null ? 0L : generator.fluidReplacements()) + "\n"
            + "cloudislands_paper_generator_island_misses_total{node=\"" + nodeId + "\"} " + (generator == null ? 0L : generator.islandMisses()) + "\n"
            + "cloudislands_paper_generator_material_failures_total{node=\"" + nodeId + "\"} " + (generator == null ? 0L : generator.materialResolveFailures()) + "\n"
            + "cloudislands_paper_generator_keys{node=\"" + nodeId + "\"} " + (generator == null ? 0 : generator.generatorKeyCount()) + "\n"
            + "cloudislands_paper_generator_rule_levels{node=\"" + nodeId + "\"} " + (generator == null ? 0 : generator.ruleLevelCount()) + "\n"
            + "cloudislands_paper_generator_cache_ttl_seconds{node=\"" + nodeId + "\"} " + (generator == null ? 0L : generator.cacheTtlSeconds()) + "\n"
            + "cloudislands_permission_checks_total{node=\"" + nodeId + "\"} " + plugin.agent().permissionCache().lookupCount() + "\n"
            + "cloudislands_permission_cache_hits_total{node=\"" + nodeId + "\"} " + plugin.agent().permissionCache().hitCount() + "\n"
            + "cloudislands_permission_cache_misses_total{node=\"" + nodeId + "\"} " + plugin.agent().permissionCache().missCount() + "\n"
            + "cloudislands_permission_cache_hit_ratio{node=\"" + nodeId + "\"} " + plugin.agent().permissionCache().hitRatio() + "\n"
            + "cloudislands_permission_cache_islands{node=\"" + nodeId + "\"} " + plugin.agent().permissionCache().cachedIslandCount() + "\n"
            + "cloudislands_protection_indexed_chunks{node=\"" + nodeId + "\"} " + protection.indexedChunkCount() + "\n"
            + "cloudislands_protection_indexed_islands{node=\"" + nodeId + "\"} " + protection.indexedIslandCount() + "\n"
            + "cloudislands_protection_migrating_islands{node=\"" + nodeId + "\"} " + protection.migratingIslandCount() + "\n"
            + "cloudislands_protection_sync_events_remote_calls{node=\"" + nodeId + "\"} 0\n"
            + "cloudislands_boundary_returns_total{node=\"" + nodeId + "\",kind=\"member\"} " + (boundary == null ? 0L : boundary.memberReturns()) + "\n"
            + "cloudislands_boundary_returns_total{node=\"" + nodeId + "\",kind=\"visitor\"} " + (boundary == null ? 0L : boundary.visitorReturns()) + "\n"
            + "cloudislands_boundary_admin_bypasses_total{node=\"" + nodeId + "\"} " + (boundary == null ? 0L : boundary.adminBypasses()) + "\n"
            + "cloudislands_paper_velocity_forwarding_required{node=\"" + nodeId + "\"} " + (forwardingRequired ? 1 : 0) + "\n"
            + "cloudislands_paper_forwarding_secret_configured{node=\"" + nodeId + "\"} " + (forwardingSecretConfigured ? 1 : 0) + "\n"
            + "cloudislands_paper_route_session_enforced{node=\"" + nodeId + "\"} " + (routeSessionEnforced ? 1 : 0) + "\n"
            + "cloudislands_paper_hide_node_names{node=\"" + nodeId + "\"} " + (hideNodeNames ? 1 : 0) + "\n"
            + "cloudislands_paper_topology_exposure_risk{node=\"" + nodeId + "\"} " + (topologyExposureRisk ? 1 : 0) + "\n"
            + "cloudislands_paper_default_node_identity_risk{node=\"" + nodeId + "\"} " + (defaultNodeIdentityRisk ? 1 : 0) + "\n"
            + "cloudislands_paper_proxy_source_allowlist_required{node=\"" + nodeId + "\"} " + (proxySourceAllowlistRequired ? 1 : 0) + "\n"
            + "cloudislands_paper_proxy_source_allowlist_configured{node=\"" + nodeId + "\"} " + (proxySourceAllowlistConfigured ? 1 : 0) + "\n"
            + "cloudislands_paper_proxy_source_allowlist_entries{node=\"" + nodeId + "\"} " + proxySourceAllowlistEntries + "\n"
            + "cloudislands_paper_direct_access_risk{node=\"" + nodeId + "\"} " + (directAccessRisk ? 1 : 0) + "\n"
            + "cloudislands_paper_velocity_online_mode_mismatch{node=\"" + nodeId + "\"} " + (velocityOnlineModeMismatch ? 1 : 0) + "\n"
            + "cloudislands_paper_bungee_connect_plugin_messaging_enabled{node=\"" + nodeId + "\"} " + (bungeeConnectPluginMessaging ? 1 : 0) + "\n"
            + "cloudislands_paper_proxy_source_configuration_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.proxySourceConfigurationRejections()) + "\n"
            + "cloudislands_paper_proxy_source_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.proxySourceRejections()) + "\n"
            + "cloudislands_paper_forwarding_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.forwardingRejections()) + "\n"
            + "cloudislands_paper_route_session_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.routeSessionRejections()) + "\n"
            + "cloudislands_paper_route_session_check_failures_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.routeSessionCheckFailures()) + "\n"
            + "cloudislands_paper_route_ticket_consume_retries_total{node=\"" + nodeId + "\"} " + routeTickets.consumeRetries() + "\n"
            + "cloudislands_paper_route_ticket_consume_failures_total{node=\"" + nodeId + "\"} " + routeTickets.consumeFailures() + "\n"
            + "cloudislands_paper_route_ticket_world_wait_retries_total{node=\"" + nodeId + "\"} " + routeTickets.worldWaitRetries() + "\n"
            + "cloudislands_paper_route_ticket_teleport_attempts_total{node=\"" + nodeId + "\"} " + routeTickets.teleportAttempts() + "\n"
            + "cloudislands_paper_route_ticket_teleport_successes_total{node=\"" + nodeId + "\"} " + routeTickets.teleportSuccesses() + "\n"
            + "cloudislands_paper_route_ticket_teleport_failures_total{node=\"" + nodeId + "\"} " + routeTickets.teleportFailures() + "\n"
            + "cloudislands_paper_chat_broadcasts_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.chatBroadcasts()) + "\n"
            + "cloudislands_paper_chat_deliveries_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.chatDeliveries()) + "\n"
            + "cloudislands_paper_chat_no_recipient_broadcasts_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.chatNoRecipientBroadcasts()) + "\n"
            + "cloudislands_paper_island_mutation_evacuations_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.islandMutationEvacuations()) + "\n"
            + "cloudislands_paper_island_mutation_fallback_transfers_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.islandMutationFallbackTransfers()) + "\n"
            + "cloudislands_paper_island_mutation_fallback_kicks_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.islandMutationFallbackKicks()) + "\n"
            + "cloudislands_paper_island_mutation_fallback_failures_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.islandMutationFallbackFailures()) + "\n"
            + "cloudislands_paper_cache_event_invalidations_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.cacheEventInvalidations()) + "\n"
            + "cloudislands_paper_cache_gap_invalidations_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.cacheGapInvalidations()) + "\n"
            + "cloudislands_redis_latency_seconds{node=\"" + nodeId + "\"} " + redis.latencySeconds() + "\n"
            + "cloudislands_paper_redis_available{node=\"" + nodeId + "\"} " + (redis.available() ? 1 : 0) + "\n"
            + "cloudislands_paper_redis_latency_seconds{node=\"" + nodeId + "\"} " + redis.latencySeconds() + "\n"
            + "cloudislands_paper_redis_failures_total{node=\"" + nodeId + "\"} " + redis.failuresTotal() + "\n"
            + (plugin.localCaches() == null ? "" : plugin.localCaches().prometheus(nodeId));
    }

    private static String jsonText(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String levelScanStatus(String supportedTemplates) {
        PeriodicIslandLevelScanTask scanner = plugin.periodicLevelScanTask();
        if (scanner == null) {
            return supportedTemplates;
        }
        java.util.UUID lastIsland = scanner.lastScannedIslandId();
        return supportedTemplates
            + ";levelScanRunning=" + scanner.running()
            + ";lastLevelScanIsland=" + (lastIsland == null ? "" : lastIsland)
            + ";lastLevelScanStartedAt=" + scanner.lastScanStartedAt()
            + ";lastLevelScanFinishedAt=" + scanner.lastScanFinishedAt()
            + ";lastLevelScanFailedAt=" + scanner.lastScanFailedAt();
    }

    String heartbeatMetadata(String supportedTemplates, MeteredIslandStorage storage) {
        PaperRedisClient.PingResult redis = plugin.redisClient() == null ? PaperRedisClient.PingResult.disabled() : plugin.redisClient().ping();
        return levelScanStatus(supportedTemplates)
            + ";localCaches=" + (plugin.localCaches() == null ? "" : plugin.localCaches().namesCsv())
            + ";localCacheInvalidations=" + (plugin.localCaches() == null ? 0L : plugin.localCaches().invalidationsTotal())
            + ";cacheEventInvalidations=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().cacheEventInvalidations())
            + ";cacheGapInvalidations=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().cacheGapInvalidations())
            + ";permissionCacheHitRatio=" + plugin.agent().permissionCache().hitRatio()
            + ";permissionChecks=" + plugin.agent().permissionCache().lookupCount()
            + ";permissionCacheHits=" + plugin.agent().permissionCache().hitCount()
            + ";permissionCacheMisses=" + plugin.agent().permissionCache().missCount()
            + ";permissionCacheIslands=" + plugin.agent().permissionCache().cachedIslandCount()
            + ";storageBackend=" + (storage == null ? "NONE" : storage.backend())
            + ";storageUploadSeconds=" + (storage == null ? 0.0D : storage.lastUploadSeconds())
            + ";storageDownloadSeconds=" + (storage == null ? 0.0D : storage.lastDownloadSeconds())
            + ";storageHealthCheckFailures=" + (storage == null ? 0L : storage.healthCheckFailures())
            + ";storageUploadFailures=" + (storage == null ? 0L : storage.uploadFailures())
            + ";storageDownloadFailures=" + (storage == null ? 0L : storage.downloadFailures())
            + ";storageOperations=" + (storage == null ? 0L : storage.operationAttempts())
            + ";storageOperationFailures=" + (storage == null ? 0L : storage.operationFailures())
            + ";storageFailureRatio=" + (storage == null ? 0.0D : storage.operationFailureRatio())
            + ";storagePrimaryDegraded=" + (storage != null && storage.primaryStorageDegraded())
            + ";storagePrimaryFailures=" + (storage == null ? 0L : storage.primaryStorageFailures())
            + ";storageFallbackReads=" + (storage == null ? 0L : storage.fallbackReads())
            + ";storageFallbackWrites=" + (storage == null ? 0L : storage.fallbackWrites())
            + ";storageFallbackDeletes=" + (storage == null ? 0L : storage.fallbackDeletes())
            + ";storageFallbackOperations=" + (storage == null ? 0L : storage.fallbackOperations())
            + ";integrationsDetected=" + integrationList(true)
            + ";integrationsMissing=" + integrationList(false)
            + ";integrationPolicy=" + kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY
            + ";objectStorageOutagePolicy=" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.CONTRACT
            + ";objectStorageSaveRetryPolicy=" + kr.lunaf.cloudislands.common.storage.StorageOutagePolicy.SAVE_RETRY_POLICY
            + ";redisAvailable=" + redis.available()
            + ";redisLatencySeconds=" + redis.latencySeconds()
            + ";redisFailures=" + redis.failuresTotal()
            + ";periodicSaveRetryQueue=" + (plugin.periodicSaveTask() == null ? 0 : plugin.periodicSaveTask().retryQueueSize())
            + ";periodicSaveFailures=" + (plugin.periodicSaveTask() == null ? 0L : plugin.periodicSaveTask().failuresTotal())
            + ";emptySaveRetryQueue=" + (plugin.emptyIslandSaveTask() == null ? 0 : plugin.emptyIslandSaveTask().retryQueueSize())
            + ";emptySaveFailures=" + (plugin.emptyIslandSaveTask() == null ? 0L : plugin.emptyIslandSaveTask().failuresTotal())
            + ";storageSaveRetryQueueTotal=" + ((plugin.periodicSaveTask() == null ? 0 : plugin.periodicSaveTask().retryQueueSize()) + (plugin.emptyIslandSaveTask() == null ? 0 : plugin.emptyIslandSaveTask().retryQueueSize()))
            + ";islandSaveFailures=" + ((plugin.periodicSaveTask() == null ? 0L : plugin.periodicSaveTask().failuresTotal()) + (plugin.emptyIslandSaveTask() == null ? 0L : plugin.emptyIslandSaveTask().failuresTotal()))
            + ";backendAccessPolicy=" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.CONTRACT
            + ";paperDirectAccessPolicy=" + kr.lunaf.cloudislands.common.security.BackendAccessPolicy.PAPER_DIRECT_ACCESS_POLICY
            + ";generatorKeys=" + (plugin.generatorListener() == null ? 0 : plugin.generatorListener().generatorKeyCount())
            + ";generatorRuleLevels=" + (plugin.generatorListener() == null ? 0 : plugin.generatorListener().ruleLevelCount())
            + ";generatorCacheTtlSeconds=" + (plugin.generatorListener() == null ? 0L : plugin.generatorListener().cacheTtlSeconds())
            + ";generatorEventPolicy=" + (plugin.generatorListener() == null ? "not-registered" : plugin.generatorListener().eventPolicy())
            + ";generatorFormEvents=" + (plugin.generatorListener() == null ? 0L : plugin.generatorListener().formEvents())
            + ";generatorFluidCollisionEvents=" + (plugin.generatorListener() == null ? 0L : plugin.generatorListener().fluidCollisionEvents())
            + ";generatorIslandMisses=" + (plugin.generatorListener() == null ? 0L : plugin.generatorListener().islandMisses())
            + ";proxySourceRejections=" + (plugin.routeSessionListener() == null ? 0L : plugin.routeSessionListener().proxySourceRejections())
            + ";forwardingRejections=" + (plugin.routeSessionListener() == null ? 0L : plugin.routeSessionListener().forwardingRejections())
            + ";routeSessionRejections=" + (plugin.routeSessionListener() == null ? 0L : plugin.routeSessionListener().routeSessionRejections())
            + ";routeSessionCheckFailures=" + (plugin.routeSessionListener() == null ? 0L : plugin.routeSessionListener().routeSessionCheckFailures())
            + ";hideNodeNames=" + config.routing().hideNodeNames()
            + ";proxySourceAllowlistRequired=" + config.security().requireProxySourceAllowlist()
            + ";proxySourceConfigurationRejections=" + (plugin.routeSessionListener() == null ? 0L : plugin.routeSessionListener().proxySourceConfigurationRejections())
            + ";playerTopologyPolicy=logical-island-only"
            + ";playerNodeNamePolicy=" + (config.routing().hideNodeNames() ? "hidden-from-player-routing-messages" : "visible-risk-admin-debug-only")
            + ";topologyExposureRisk=" + !config.routing().hideNodeNames()
            + ";nodePoolScalePolicy=count-independent-requires-unique-node-id-unique-velocity-server-name-shared-bundle-storage"
            + ";fiveSixNodePoolPolicy=supported-when-each-island-node-registers-as-a-distinct-route-candidate"
            + ";nodeIdentityPolicy=node.id-and-velocity-server-name-must-be-unique-per-island-node"
            + ";defaultNodeIdentityReject=" + config.node().rejectDefaultIdentity()
            + ";defaultNodeIdentityRisk=" + plugin.defaultNodeIdentityRisk(config.node().role(), config.node().id(), config.node().velocityServerName())
            + ";chatBroadcasts=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().chatBroadcasts())
            + ";chatDeliveries=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().chatDeliveries())
            + ";chatNoRecipientBroadcasts=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().chatNoRecipientBroadcasts())
            + ";islandMutationEvacuations=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().islandMutationEvacuations())
            + ";islandMutationFallbackTransfers=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().islandMutationFallbackTransfers())
            + ";islandMutationFallbackKicks=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().islandMutationFallbackKicks())
            + ";islandMutationFallbackFailures=" + (plugin.permissionEventPoller() == null ? 0L : plugin.permissionEventPoller().islandMutationFallbackFailures());
    }

    private String integrationList(boolean enabled) {
        return kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy.knownPlugins().stream()
            .filter(pluginName -> plugin.getServer().getPluginManager().isPluginEnabled(pluginName) == enabled)
            .collect(java.util.stream.Collectors.joining(","));
    }
}
