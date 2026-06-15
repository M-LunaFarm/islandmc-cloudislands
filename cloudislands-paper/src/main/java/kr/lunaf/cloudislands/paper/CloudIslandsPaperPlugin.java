package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.CloudIslandsProvider;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.paper.api.PaperCloudIslandsApi;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.EmptyIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.IslandDeactivationHandler;
import kr.lunaf.cloudislands.paper.activation.IslandSaveService;
import kr.lunaf.cloudislands.paper.activation.PeriodicIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.ShardWorldManager;
import kr.lunaf.cloudislands.paper.admin.AdminCommandController;
import kr.lunaf.cloudislands.paper.cache.PermissionEventPoller;
import kr.lunaf.cloudislands.paper.cache.PermissionCacheSyncService;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.command.IslandCommandController;
import kr.lunaf.cloudislands.paper.economy.VaultEconomyBridge;
import kr.lunaf.cloudislands.paper.generator.ConfigGeneratorRules;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.generator.IslandCropGrowthListener;
import kr.lunaf.cloudislands.paper.generator.IslandGeneratorListener;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBankMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBanMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBiomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandChatMenu;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
import kr.lunaf.cloudislands.paper.gui.IslandDangerMenu;
import kr.lunaf.cloudislands.paper.gui.IslandFlagMenu;
import kr.lunaf.cloudislands.paper.gui.IslandHomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInfoMenu;
import kr.lunaf.cloudislands.paper.gui.IslandInviteMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLimitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandLogMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMainMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMemberMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandMyIslandsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandPermissionMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRankingMenu;
import kr.lunaf.cloudislands.paper.gui.IslandRoleMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSettingsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSnapshotMenu;
import kr.lunaf.cloudislands.paper.gui.IslandUpgradeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandWarpMenu;
import kr.lunaf.cloudislands.paper.health.PaperHealthService;
import kr.lunaf.cloudislands.paper.heartbeat.PaperHeartbeatService;
import kr.lunaf.cloudislands.paper.job.CoreBackedIslandJobSource;
import kr.lunaf.cloudislands.paper.job.PaperIslandJobWorker;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import kr.lunaf.cloudislands.paper.level.IslandLevelScanService;
import kr.lunaf.cloudislands.paper.level.PeriodicIslandLevelScanTask;
import kr.lunaf.cloudislands.paper.limit.IslandEntityLimitListener;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import kr.lunaf.cloudislands.paper.limit.IslandLimitListener;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import kr.lunaf.cloudislands.paper.message.TranslationManager;
import kr.lunaf.cloudislands.paper.placeholder.CloudIslandsPlaceholderExpansion;
import kr.lunaf.cloudislands.paper.redis.PaperRedisClient;
import kr.lunaf.cloudislands.paper.security.ProxySourceAllowlist;
import kr.lunaf.cloudislands.paper.session.PaperBrandingListener;
import kr.lunaf.cloudislands.paper.session.PaperChatListener;
import kr.lunaf.cloudislands.paper.session.PaperPlayerProfileListener;
import kr.lunaf.cloudislands.paper.session.PaperScoreboardListener;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import kr.lunaf.cloudislands.paper.storage.MeteredIslandStorage;
import kr.lunaf.cloudislands.paper.storage.PaperStorageFactory;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.paper.world.bundle.ExternalTarBundleExtractor;
import kr.lunaf.cloudislands.paper.world.ShardWorldPreloader;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.export.ExternalTarIslandBundleExporter;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    private CloudIslandsPaperAgent agent;
    private PaperHeartbeatService heartbeatService;
    private PaperIslandJobWorker jobWorker;
    private PermissionEventPoller permissionEventPoller;
    private PeriodicIslandSaveTask periodicSaveTask;
    private EmptyIslandSaveTask emptyIslandSaveTask;
    private PeriodicIslandLevelScanTask periodicLevelScanTask;
    private PaperHealthService healthService;
    private ActiveIslandRegistry activeIslands;
    private CloudIslandsApi api;
    private EconomyBridge economyBridge;
    private GeneratorLevelCache generatorLevels;
    private IslandGeneratorListener generatorListener;
    private PaperRouteSessionListener routeSessionListener;
    private IslandBoundaryListener boundaryListener;
    private Object placeholderExpansion;
    private MessageRenderer messages;
    private PaperRedisClient redisClient;
    private LocalCacheManager localCaches;
    private ProxySourceAllowlist proxySourceAllowlist;
    private MeteredIslandStorage islandStorage;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        logSecurityPosture();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "minecraft:brand");
        if (configBoolean("security.allow-bungee-connect-plugin-messaging", false)) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }
        String nodeId = getConfig().getString("node.id", "island-1");
        String pool = getConfig().getString("node.pool", "island");
        String velocityServerName = getConfig().getString("node.velocity-server-name", nodeId);
        AgentRole role = parseAgentRole(getConfig().getString("node.role", "ISLAND_NODE"));
        warnIfDefaultNodeIdentity(role, nodeId, velocityServerName);
        CoreApiClient client = new JdkCoreApiClient(
            URI.create(coreApiBaseUrl()),
            coreApiToken(),
            coreAdminToken(),
            Duration.ofMillis(coreApiTimeoutMillis())
        );
        this.agent = new CloudIslandsPaperAgent(this, role, client, nodeId);
        this.localCaches = new LocalCacheManager();
        localCaches.registerStats("permissions", agent.permissionCache()::invalidateAll, agent.permissionCache()::lookupCount, agent.permissionCache()::hitRatio);
        String serviceName = getConfig().getString("plugin.service-name", "CloudIslands");
        this.messages = new MessageRenderer(TranslationManager.fromConfig(getConfig(), serviceName));
        agent.routeTickets().setMessages(messages);
        this.redisClient = PaperRedisClient.create(
            resolveEnv(getConfig().getString("redis.uri", "redis://redis.internal:6379")),
            Duration.ofMillis(Math.max(1L, getConfig().getLong("redis.timeout-ms", 1000L)))
        );
        this.api = new PaperCloudIslandsApi(client, agent);
        CloudIslandsProvider.set(api);
        getServer().getServicesManager().register(CloudIslandsApi.class, api, this, ServicePriority.Normal);
        this.economyBridge = new VaultEconomyBridge(getServer());
        getServer().getServicesManager().register(EconomyBridge.class, economyBridge, this, ServicePriority.Normal);
        registerPlaceholderExpansion(client);
        IslandLimitCache limitCache = new IslandLimitCache(client);
        localCaches.register("limits", limitCache::invalidateAll);
        long denyMessageCooldownMs = getConfig().getLong("protection.deny-message-cooldown-ms", 1000L);
        BlockDeltaReporter blockDeltas = new BlockDeltaReporter(this, client);
        getServer().getPluginManager().registerEvents(new PaperPlayerProfileListener(client), this);
        getServer().getPluginManager().registerEvents(new PaperBrandingListener(this, messages), this);
        getServer().getPluginManager().registerEvents(new PaperChatListener(messages), this);
        getServer().getPluginManager().registerEvents(new PaperScoreboardListener(this, messages), this);
        getServer().getPluginManager().registerEvents(new AdminNodeMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandBankMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandBanMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandBiomeMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandChatMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandCreateMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandDangerMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandFlagMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandHomeMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandInfoMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandInviteMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandLimitMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandLogMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandMainMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandMemberMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandMissionMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandMyIslandsMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandPermissionMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandRankingMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandRoleMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandSettingsMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandSnapshotMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandUpgradeMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandVisitMenu(messages), this);
        getServer().getPluginManager().registerEvents(new IslandWarpMenu(messages), this);
        if (role == AgentRole.ISLAND_NODE) {
            getServer().getPluginManager().registerEvents(new IslandProtectionListener(agent.protection(), blockDeltas, denyMessageCooldownMs, denyMessages()), this);
            this.boundaryListener = new IslandBoundaryListener(agent.protection(), messages);
            getServer().getPluginManager().registerEvents(boundaryListener, this);
            getServer().getPluginManager().registerEvents(new IslandGameplayFlagListener(agent.protection(), messages), this);
            getServer().getPluginManager().registerEvents(new IslandLimitListener(agent.protection(), limitCache, messages), this);
            getServer().getPluginManager().registerEvents(new IslandEntityLimitListener(agent.protection(), limitCache, messages), this);
            this.generatorLevels = new GeneratorLevelCache(client, getConfig().getString("generators.default-key", "default"));
            CropGrowthLevelCache cropGrowthLevels = new CropGrowthLevelCache(client);
            localCaches.register("generator-levels", generatorLevels::invalidateAll);
            localCaches.register("crop-growth-levels", cropGrowthLevels::invalidateAll);
            this.generatorListener = new IslandGeneratorListener(agent.protection(), ConfigGeneratorRules.load(this), generatorLevels, blockDeltas);
            getServer().getPluginManager().registerEvents(generatorListener, this);
            getServer().getPluginManager().registerEvents(new IslandCropGrowthListener(agent.protection(), cropGrowthLevels), this);
        }
        String fallbackServerName = getConfig().getString("routing.fallback-on-failure", "Lobby");
        boolean enforceRouteSession = role == AgentRole.ISLAND_NODE && configBoolean("security.enforce-route-session", true);
        boolean requireRouteSession = role == AgentRole.ISLAND_NODE && (configBoolean("routing.require-route-session", true) || enforceRouteSession);
        boolean forwardingReady = role != AgentRole.ISLAND_NODE
            || !configBoolean("security.require-velocity-forwarding", true)
            || !resolveEnv(getConfig().getString("security.forwarding-secret", "")).isBlank();
        this.proxySourceAllowlist = new ProxySourceAllowlist(getConfig().getStringList("security.proxy-source-allowlist"));
        this.routeSessionListener = new PaperRouteSessionListener(this, client, agent.routeTickets(), nodeId, requireRouteSession, forwardingReady, fallbackServerName, proxySourceAllowlist, messages);
        getServer().getPluginManager().registerEvents(routeSessionListener, this);
        PluginCommand admin = getCommand("ciadmin");
        int routeWaitSeconds = getConfig().getInt("routing.wait-for-activation-timeout-seconds", 20);
        if (admin != null) {
            AdminCommandController adminController = new AdminCommandController(agent, client, nodeId, routeWaitSeconds, localCaches, messages);
            admin.setExecutor(adminController);
            admin.setTabCompleter(adminController);
        }
        PluginCommand island = getCommand("island");
        if (island != null) {
            IslandLevelScanService levelScanService = new IslandLevelScanService(this, () -> activeIslands, client);
            IslandCommandController islandController = new IslandCommandController(this, client, agent.protection(), routeWaitSeconds, fallbackServerName, levelScanService, economyBridge, messages);
            island.setExecutor(islandController);
            island.setTabCompleter(islandController);
            getServer().getPluginManager().registerEvents(islandController, this);
        }
        MeteredIslandStorage storage = role == AgentRole.ISLAND_NODE ? new MeteredIslandStorage(PaperStorageFactory.create(this, getConfig()), PaperStorageFactory.backendName(getConfig())) : null;
        this.islandStorage = storage;
        String supportedTemplates = String.join(",", getConfig().getStringList("node.supported-templates"));
        if (supportedTemplates.isBlank()) {
            supportedTemplates = getConfig().getString("node.supported-template", "*");
        }
        int maxActivationQueue = Math.max(1, getConfig().getInt("island-node.activation.max-concurrent", 4));
        int hardPlayerCap = Math.max(1, getConfig().getInt("node.hard-player-cap", 110));
        int reservedSlots = Math.max(0, getConfig().getInt("node.reserved-slots", 15));
        int reservedSoftCap = Math.max(1, hardPlayerCap - reservedSlots);
        int softPlayerCap = getConfig().contains("node.soft-player-cap")
            ? Math.max(1, Math.min(reservedSoftCap, getConfig().getInt("node.soft-player-cap", reservedSoftCap)))
            : reservedSoftCap;
        int maxActiveIslands = Math.max(1, getConfig().getInt("node.max-active-islands", 600));
        this.heartbeatService = new PaperHeartbeatService(
            this,
            client,
            nodeId,
            pool,
            velocityServerName,
            getDescription().getVersion(),
            supportedTemplates,
            () -> heartbeatMetadata(supportedTemplates, storage),
            () -> storageAvailable(storage),
            () -> softPlayerCap,
            () -> hardPlayerCap,
            () -> reservedSlots,
            () -> activeIslands == null ? 0 : activeIslands.size(),
            () -> maxActiveIslands,
            () -> jobWorker == null ? 0 : jobWorker.activationQueue(),
            () -> maxActivationQueue,
            () -> activeIslands == null ? 0.0D : Math.min(1.5D, (double) activeIslands.size() / maxActiveIslands),
            () -> jobWorker == null ? 0 : jobWorker.recentFailurePenalty()
        );
        heartbeatService.start(getConfig().getLong("heartbeat.interval-ticks", 20L));
        if (getConfig().getBoolean("health.enabled", false)) {
            this.healthService = new PaperHealthService(
                this,
                getConfig().getString("health.bind-host", "127.0.0.1"),
                getConfig().getInt("health.port", 8787),
                () -> paperHealthJson(role, nodeId),
                () -> paperMetricsText(role, nodeId, storage)
            );
            healthService.start();
        }
        if (role == AgentRole.ISLAND_NODE) {
            startIslandNodeWorker(client, nodeId, storage, limitCache);
        }
        getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    @Override
    public void onDisable() {
        if (jobWorker != null) {
            jobWorker.stop();
        }
        if (permissionEventPoller != null) {
            permissionEventPoller.stop();
        }
        if (periodicSaveTask != null) {
            periodicSaveTask.stop();
        }
        if (emptyIslandSaveTask != null) {
            emptyIslandSaveTask.stop();
        }
        if (periodicLevelScanTask != null) {
            periodicLevelScanTask.stop();
        }
        if (heartbeatService != null) {
            heartbeatService.stop();
        }
        if (healthService != null) {
            healthService.stop();
            healthService = null;
        }
        if (redisClient != null) {
            redisClient.close();
            redisClient = null;
        }
        islandStorage = null;
        generatorListener = null;
        if (localCaches != null) {
            localCaches.invalidateAll();
            localCaches = null;
        }
        unregisterPlaceholderExpansion();
        if (api != null) {
            CloudIslandsProvider.clear(api);
            getServer().getServicesManager().unregister(CloudIslandsApi.class, api);
            api = null;
        }
        if (economyBridge != null) {
            getServer().getServicesManager().unregister(EconomyBridge.class, economyBridge);
            economyBridge = null;
        }
        messages = null;
    }

    public CloudIslandsPaperAgent agent() {
        return agent;
    }

    public ActiveIslandRegistry activeIslands() {
        return activeIslands;
    }

    public PaperIslandJobWorker jobWorker() {
        return jobWorker;
    }

    private void registerPlaceholderExpansion(CoreApiClient client) {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        try {
            CloudIslandsPlaceholderExpansion expansion = new CloudIslandsPlaceholderExpansion(this, client);
            if (expansion.register()) {
                this.placeholderExpansion = expansion;
                getLogger().info("Registered PlaceholderAPI expansion: cloudislands");
            }
        } catch (LinkageError error) {
            getLogger().warning("PlaceholderAPI was detected but the CloudIslands expansion could not be registered: " + error.getMessage());
        }
    }

    private void unregisterPlaceholderExpansion() {
        Object expansion = placeholderExpansion;
        placeholderExpansion = null;
        if (expansion == null) {
            return;
        }
        try {
            expansion.getClass().getMethod("unregister").invoke(expansion);
        } catch (ReflectiveOperationException ignored) {
            // PlaceholderAPI handles plugin-disable cleanup when explicit unregister is unavailable.
        }
    }

    private String paperHealthJson(AgentRole role, String nodeId) {
        PaperRedisClient.PingResult redis = redisClient == null ? PaperRedisClient.PingResult.disabled() : redisClient.ping();
        boolean forwardingRequired = configBoolean("security.require-velocity-forwarding", true);
        boolean forwardingSecretConfigured = !resolveEnv(getConfig().getString("security.forwarding-secret", "")).isBlank();
        boolean routeSessionEnforced = configBoolean("security.enforce-route-session", true) || configBoolean("routing.require-route-session", true);
        boolean hideNodeNames = configBoolean("routing.hide-node-names", true);
        boolean topologyExposureRisk = !hideNodeNames;
        boolean defaultNodeIdentityRisk = defaultNodeIdentityRisk(role, nodeId, getConfig().getString("node.velocity-server-name", nodeId));
        int proxySourceAllowlistEntries = proxySourceAllowlist == null ? 0 : proxySourceAllowlist.entryCount();
        boolean proxySourceAllowlistConfigured = proxySourceAllowlistEntries > 0;
        boolean directAccessRisk = role == AgentRole.ISLAND_NODE && !getServer().getOnlineMode() && !proxySourceAllowlistConfigured;
        boolean velocityOnlineModeMismatch = role == AgentRole.ISLAND_NODE && forwardingRequired && getServer().getOnlineMode();
        boolean bungeeConnectPluginMessaging = configBoolean("security.allow-bungee-connect-plugin-messaging", false);
        boolean bungeeConnectChannelRegistered = getServer().getMessenger().isOutgoingChannelRegistered(this, "BungeeCord");
        boolean islandNodeRole = role == AgentRole.ISLAND_NODE;
        boolean worldExecutionEnabled = islandNodeRole && activeIslands != null;
        boolean activationWorkerEnabled = islandNodeRole && jobWorker != null;
        boolean islandProtectionEnabled = islandNodeRole;
        boolean islandSaveTasksEnabled = islandNodeRole && (periodicSaveTask != null || emptyIslandSaveTask != null);
        PaperRouteSessionListener routeSessions = routeSessionListener;
        PermissionEventPoller events = permissionEventPoller;
        PeriodicIslandSaveTask saver = periodicSaveTask;
        EmptyIslandSaveTask emptySaver = emptyIslandSaveTask;
        ProtectionController protection = agent == null ? null : agent.protection();
        IslandBoundaryListener boundary = boundaryListener;
        MeteredIslandStorage storage = islandStorage;
        return "{"
            + "\"status\":\"UP\","
            + "\"role\":\"" + role.name() + "\","
            + "\"nodeId\":\"" + nodeId + "\","
            + "\"onlineMode\":" + getServer().getOnlineMode() + ","
            + "\"onlinePlayers\":" + getServer().getOnlinePlayers().size() + ","
            + "\"rolePolicy\":\"" + (islandNodeRole ? "island-node-runs-worlds-protection-routing-and-saves" : "lobby-runs-gui-query-and-routing-entry-only") + "\","
            + "\"roleWorldExecutionEnabled\":" + worldExecutionEnabled + ","
            + "\"roleActivationWorkerEnabled\":" + activationWorkerEnabled + ","
            + "\"roleIslandProtectionEnabled\":" + islandProtectionEnabled + ","
            + "\"roleIslandSaveTasksEnabled\":" + islandSaveTasksEnabled + ","
            + "\"protectionDecisionPolicy\":\"" + (protection == null ? "unavailable" : protection.synchronousDecisionPolicy()) + "\","
            + "\"protectionSynchronousEventsUseRemoteCalls\":false,"
            + "\"protectionIndexedChunks\":" + (protection == null ? 0 : protection.indexedChunkCount()) + ","
            + "\"protectionIndexedIslands\":" + (protection == null ? 0 : protection.indexedIslandCount()) + ","
            + "\"protectionMigratingIslands\":" + (protection == null ? 0 : protection.migratingIslandCount()) + ","
            + "\"boundaryMemberReturnsTotal\":" + (boundary == null ? 0L : boundary.memberReturns()) + ","
            + "\"boundaryVisitorReturnsTotal\":" + (boundary == null ? 0L : boundary.visitorReturns()) + ","
            + "\"boundaryAdminBypassesTotal\":" + (boundary == null ? 0L : boundary.adminBypasses()) + ","
            + "\"activeIslands\":" + (activeIslands == null ? 0 : activeIslands.size()) + ","
            + "\"activationQueue\":" + (jobWorker == null ? 0 : jobWorker.activationQueue()) + ","
            + "\"storageBackend\":\"" + (storage == null ? "NONE" : storage.backend()) + "\","
            + "\"storagePrimaryDegraded\":" + (storage != null && storage.primaryStorageDegraded()) + ","
            + "\"storagePrimaryFailuresTotal\":" + (storage == null ? 0L : storage.primaryStorageFailures()) + ","
            + "\"storageFallbackReadsTotal\":" + (storage == null ? 0L : storage.fallbackReads()) + ","
            + "\"storageFallbackWritesTotal\":" + (storage == null ? 0L : storage.fallbackWrites()) + ","
            + "\"storageFallbackDeletesTotal\":" + (storage == null ? 0L : storage.fallbackDeletes()) + ","
            + "\"storageFallbackOperationsTotal\":" + (storage == null ? 0L : storage.fallbackOperations()) + ","
            + "\"storageUploadFailuresTotal\":" + (storage == null ? 0L : storage.uploadFailures()) + ","
            + "\"storageDownloadFailuresTotal\":" + (storage == null ? 0L : storage.downloadFailures()) + ","
            + "\"storageOperationFailuresTotal\":" + (storage == null ? 0L : storage.operationFailures()) + ","
            + "\"redisAvailable\":" + redis.available() + ","
            + "\"redisLatencySeconds\":" + redis.latencySeconds() + ","
            + "\"redisFailuresTotal\":" + redis.failuresTotal() + ","
            + "\"velocityForwardingModePolicy\":\"modern-forwarding-required\","
            + "\"velocityForwardingRequired\":" + forwardingRequired + ","
            + "\"forwardingSecretConfigured\":" + forwardingSecretConfigured + ","
            + "\"forwardingSecretPolicy\":\"required-when-velocity-forwarding-required\","
            + "\"paperDirectAccessPolicy\":\"deny-backend-direct-player-connections\","
            + "\"paperOnlineModePolicy\":\"false-only-behind-velocity-with-forwarding-secret\","
            + "\"backendFirewallPolicy\":\"paper-redis-database-and-object-storage-private-only\","
            + "\"routeSessionEnforced\":" + routeSessionEnforced + ","
            + "\"hideNodeNames\":" + hideNodeNames + ","
            + "\"playerTopologyPolicy\":\"logical-island-only\","
            + "\"playerNodeNamePolicy\":\"" + (hideNodeNames ? "hidden-from-player-routing-messages" : "visible-risk-admin-debug-only") + "\","
            + "\"topologyExposureRisk\":" + topologyExposureRisk + ","
            + "\"defaultNodeIdentityRisk\":" + defaultNodeIdentityRisk + ","
            + "\"proxySourceAllowlistConfigured\":" + proxySourceAllowlistConfigured + ","
            + "\"proxySourceAllowlistEntries\":" + proxySourceAllowlistEntries + ","
            + "\"directAccessRisk\":" + directAccessRisk + ","
            + "\"velocityOnlineModeMismatch\":" + velocityOnlineModeMismatch + ","
            + "\"pluginMessagingControlPolicy\":\"forbidden-for-core-control\","
            + "\"bungeeConnectPluginMessagingEnabled\":" + bungeeConnectPluginMessaging + ","
            + "\"bungeeConnectChannelRegistered\":" + bungeeConnectChannelRegistered + ","
            + "\"bungeeConnectUseCase\":\"fallback-transfer-only\","
            + "\"proxySourceRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.proxySourceRejections()) + ","
            + "\"forwardingRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.forwardingRejections()) + ","
            + "\"routeSessionRejectionsTotal\":" + (routeSessions == null ? 0L : routeSessions.routeSessionRejections()) + ","
            + "\"routeSessionCheckFailuresTotal\":" + (routeSessions == null ? 0L : routeSessions.routeSessionCheckFailures()) + ","
            + "\"chatBroadcastsTotal\":" + (events == null ? 0L : events.chatBroadcasts()) + ","
            + "\"chatDeliveriesTotal\":" + (events == null ? 0L : events.chatDeliveries()) + ","
            + "\"chatNoRecipientBroadcastsTotal\":" + (events == null ? 0L : events.chatNoRecipientBroadcasts()) + ","
            + "\"cacheEventInvalidationsTotal\":" + (events == null ? 0L : events.cacheEventInvalidations()) + ","
            + "\"cacheGapInvalidationsTotal\":" + (events == null ? 0L : events.cacheGapInvalidations()) + ","
            + "\"periodicSaveRetryQueue\":" + (saver == null ? 0 : saver.retryQueueSize()) + ","
            + "\"periodicSaveFailuresTotal\":" + (saver == null ? 0L : saver.failuresTotal()) + ","
            + "\"emptySaveRetryQueue\":" + (emptySaver == null ? 0 : emptySaver.retryQueueSize()) + ","
            + "\"emptySaveFailuresTotal\":" + (emptySaver == null ? 0L : emptySaver.failuresTotal()) + ","
            + "\"localCacheCount\":" + (localCaches == null ? 0 : localCaches.cacheCount()) + ","
            + "\"localCacheInvalidationsTotal\":" + (localCaches == null ? 0 : localCaches.invalidationsTotal())
            + "}";
    }

    private String paperMetricsText(AgentRole role, String nodeId, MeteredIslandStorage storage) {
        int active = activeIslands == null ? 0 : activeIslands.size();
        int queue = jobWorker == null ? 0 : jobWorker.activationQueue();
        int failures = jobWorker == null ? 0 : jobWorker.recentFailurePenalty();
        PaperRedisClient.PingResult redis = redisClient == null ? PaperRedisClient.PingResult.disabled() : redisClient.ping();
        double storageUploadSeconds = storage == null ? 0.0D : storage.lastUploadSeconds();
        double storageDownloadSeconds = storage == null ? 0.0D : storage.lastDownloadSeconds();
        long storageFailures = storage == null ? 0L : storage.operationFailures();
        String storageBackend = storage == null ? "NONE" : storage.backend();
        boolean primaryStorageDegraded = storage != null && storage.primaryStorageDegraded();
        long primaryStorageFailures = storage == null ? 0L : storage.primaryStorageFailures();
        long storageFallbackReads = storage == null ? 0L : storage.fallbackReads();
        long storageFallbackWrites = storage == null ? 0L : storage.fallbackWrites();
        long storageFallbackDeletes = storage == null ? 0L : storage.fallbackDeletes();
        long storageFallbackOperations = storage == null ? 0L : storage.fallbackOperations();
        boolean forwardingRequired = configBoolean("security.require-velocity-forwarding", true);
        boolean forwardingSecretConfigured = !resolveEnv(getConfig().getString("security.forwarding-secret", "")).isBlank();
        boolean routeSessionEnforced = configBoolean("security.enforce-route-session", true) || configBoolean("routing.require-route-session", true);
        boolean hideNodeNames = configBoolean("routing.hide-node-names", true);
        boolean topologyExposureRisk = !hideNodeNames;
        boolean defaultNodeIdentityRisk = defaultNodeIdentityRisk(role, nodeId, getConfig().getString("node.velocity-server-name", nodeId));
        int proxySourceAllowlistEntries = proxySourceAllowlist == null ? 0 : proxySourceAllowlist.entryCount();
        boolean proxySourceAllowlistConfigured = proxySourceAllowlistEntries > 0;
        boolean directAccessRisk = role == AgentRole.ISLAND_NODE && !getServer().getOnlineMode() && !proxySourceAllowlistConfigured;
        boolean velocityOnlineModeMismatch = role == AgentRole.ISLAND_NODE && forwardingRequired && getServer().getOnlineMode();
        boolean bungeeConnectPluginMessaging = configBoolean("security.allow-bungee-connect-plugin-messaging", false);
        PeriodicIslandLevelScanTask scanner = periodicLevelScanTask;
        PeriodicIslandSaveTask saver = periodicSaveTask;
        EmptyIslandSaveTask emptySaver = emptyIslandSaveTask;
        IslandGeneratorListener generator = generatorListener;
        PaperRouteSessionListener routeSessions = routeSessionListener;
        PermissionEventPoller events = permissionEventPoller;
        ProtectionController protection = agent.protection();
        IslandBoundaryListener boundary = boundaryListener;
        return ""
            + "cloudislands_paper_online_players " + getServer().getOnlinePlayers().size() + "\n"
            + "cloudislands_paper_online_mode " + (getServer().getOnlineMode() ? 1 : 0) + "\n"
            + "cloudislands_paper_active_islands{node=\"" + nodeId + "\",role=\"" + role.name() + "\"} " + active + "\n"
            + "cloudislands_paper_activation_queue{node=\"" + nodeId + "\"} " + queue + "\n"
            + "cloudislands_paper_recent_failure_penalty{node=\"" + nodeId + "\"} " + failures + "\n"
            + "cloudislands_storage_upload_seconds{node=\"" + nodeId + "\"} " + storageUploadSeconds + "\n"
            + "cloudislands_storage_download_seconds{node=\"" + nodeId + "\"} " + storageDownloadSeconds + "\n"
            + "cloudislands_storage_operation_failures_total{node=\"" + nodeId + "\"} " + storageFailures + "\n"
            + "cloudislands_storage_backend{node=\"" + nodeId + "\",backend=\"" + storageBackend + "\"} " + (storage == null ? 0 : 1) + "\n"
            + "cloudislands_storage_primary_degraded{node=\"" + nodeId + "\"} " + (primaryStorageDegraded ? 1 : 0) + "\n"
            + "cloudislands_storage_primary_failures_total{node=\"" + nodeId + "\"} " + primaryStorageFailures + "\n"
            + "cloudislands_storage_fallback_reads_total{node=\"" + nodeId + "\"} " + storageFallbackReads + "\n"
            + "cloudislands_storage_fallback_writes_total{node=\"" + nodeId + "\"} " + storageFallbackWrites + "\n"
            + "cloudislands_storage_fallback_deletes_total{node=\"" + nodeId + "\"} " + storageFallbackDeletes + "\n"
            + "cloudislands_storage_fallback_operations_total{node=\"" + nodeId + "\"} " + storageFallbackOperations + "\n"
            + "cloudislands_island_save_seconds{node=\"" + nodeId + "\"} " + storageUploadSeconds + "\n"
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
            + "cloudislands_paper_generator_replacements_total{node=\"" + nodeId + "\",source=\"block_form\"} " + (generator == null ? 0L : generator.formReplacements()) + "\n"
            + "cloudislands_paper_generator_replacements_total{node=\"" + nodeId + "\",source=\"fluid_collision\"} " + (generator == null ? 0L : generator.fluidReplacements()) + "\n"
            + "cloudislands_paper_generator_material_failures_total{node=\"" + nodeId + "\"} " + (generator == null ? 0L : generator.materialResolveFailures()) + "\n"
            + "cloudislands_paper_generator_keys{node=\"" + nodeId + "\"} " + (generator == null ? 0 : generator.generatorKeyCount()) + "\n"
            + "cloudislands_paper_generator_rule_levels{node=\"" + nodeId + "\"} " + (generator == null ? 0 : generator.ruleLevelCount()) + "\n"
            + "cloudislands_paper_generator_cache_ttl_seconds{node=\"" + nodeId + "\"} " + (generator == null ? 0L : generator.cacheTtlSeconds()) + "\n"
            + "cloudislands_permission_checks_total{node=\"" + nodeId + "\"} " + agent.permissionCache().lookupCount() + "\n"
            + "cloudislands_permission_cache_hit_ratio{node=\"" + nodeId + "\"} " + agent.permissionCache().hitRatio() + "\n"
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
            + "cloudislands_paper_proxy_source_allowlist_configured{node=\"" + nodeId + "\"} " + (proxySourceAllowlistConfigured ? 1 : 0) + "\n"
            + "cloudislands_paper_proxy_source_allowlist_entries{node=\"" + nodeId + "\"} " + proxySourceAllowlistEntries + "\n"
            + "cloudislands_paper_direct_access_risk{node=\"" + nodeId + "\"} " + (directAccessRisk ? 1 : 0) + "\n"
            + "cloudislands_paper_velocity_online_mode_mismatch{node=\"" + nodeId + "\"} " + (velocityOnlineModeMismatch ? 1 : 0) + "\n"
            + "cloudislands_paper_bungee_connect_plugin_messaging_enabled{node=\"" + nodeId + "\"} " + (bungeeConnectPluginMessaging ? 1 : 0) + "\n"
            + "cloudislands_paper_proxy_source_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.proxySourceRejections()) + "\n"
            + "cloudislands_paper_forwarding_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.forwardingRejections()) + "\n"
            + "cloudislands_paper_route_session_rejections_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.routeSessionRejections()) + "\n"
            + "cloudislands_paper_route_session_check_failures_total{node=\"" + nodeId + "\"} " + (routeSessions == null ? 0L : routeSessions.routeSessionCheckFailures()) + "\n"
            + "cloudislands_paper_chat_broadcasts_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.chatBroadcasts()) + "\n"
            + "cloudislands_paper_chat_deliveries_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.chatDeliveries()) + "\n"
            + "cloudislands_paper_chat_no_recipient_broadcasts_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.chatNoRecipientBroadcasts()) + "\n"
            + "cloudislands_paper_cache_event_invalidations_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.cacheEventInvalidations()) + "\n"
            + "cloudislands_paper_cache_gap_invalidations_total{node=\"" + nodeId + "\"} " + (events == null ? 0L : events.cacheGapInvalidations()) + "\n"
            + "cloudislands_redis_latency_seconds{node=\"" + nodeId + "\"} " + redis.latencySeconds() + "\n"
            + "cloudislands_paper_redis_available{node=\"" + nodeId + "\"} " + (redis.available() ? 1 : 0) + "\n"
            + "cloudislands_paper_redis_latency_seconds{node=\"" + nodeId + "\"} " + redis.latencySeconds() + "\n"
            + "cloudislands_paper_redis_failures_total{node=\"" + nodeId + "\"} " + redis.failuresTotal() + "\n"
            + (localCaches == null ? "" : localCaches.prometheus(nodeId));
    }

    private AgentRole parseAgentRole(String configuredRole) {
        String normalized = configuredRole == null ? "" : configuredRole.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) {
            return AgentRole.ISLAND_NODE;
        }
        try {
            return AgentRole.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Unknown CloudIslands Paper node.role '" + configuredRole + "', using ISLAND_NODE.");
            return AgentRole.ISLAND_NODE;
        }
    }

    private void warnIfDefaultNodeIdentity(AgentRole role, String nodeId, String velocityServerName) {
        if (!defaultNodeIdentityRisk(role, nodeId, velocityServerName)) {
            return;
        }
        getLogger().warning("CloudIslands node.id/velocity-server-name still use the default island-1/Island-1 identity. Set a unique node.id and Velocity server name for every Island node before running multiple Island servers.");
    }

    private boolean defaultNodeIdentityRisk(AgentRole role, String nodeId, String velocityServerName) {
        if (role != AgentRole.ISLAND_NODE) {
            return false;
        }
        String safeNodeId = nodeId == null ? "" : nodeId.trim();
        String safeVelocityServerName = velocityServerName == null ? "" : velocityServerName.trim();
        return safeNodeId.equalsIgnoreCase("island-1") || safeVelocityServerName.equalsIgnoreCase("Island-1");
    }

    private String levelScanStatus(String supportedTemplates) {
        PeriodicIslandLevelScanTask scanner = periodicLevelScanTask;
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

    private String heartbeatMetadata(String supportedTemplates, MeteredIslandStorage storage) {
        return levelScanStatus(supportedTemplates)
            + ";localCaches=" + (localCaches == null ? "" : localCaches.namesCsv())
            + ";localCacheInvalidations=" + (localCaches == null ? 0L : localCaches.invalidationsTotal())
            + ";cacheEventInvalidations=" + (permissionEventPoller == null ? 0L : permissionEventPoller.cacheEventInvalidations())
            + ";cacheGapInvalidations=" + (permissionEventPoller == null ? 0L : permissionEventPoller.cacheGapInvalidations())
            + ";permissionCacheHitRatio=" + agent.permissionCache().hitRatio()
            + ";permissionChecks=" + agent.permissionCache().lookupCount()
            + ";storageBackend=" + (storage == null ? "NONE" : storage.backend())
            + ";storageUploadSeconds=" + (storage == null ? 0.0D : storage.lastUploadSeconds())
            + ";storageDownloadSeconds=" + (storage == null ? 0.0D : storage.lastDownloadSeconds())
            + ";storageHealthCheckFailures=" + (storage == null ? 0L : storage.healthCheckFailures())
            + ";storageUploadFailures=" + (storage == null ? 0L : storage.uploadFailures())
            + ";storageDownloadFailures=" + (storage == null ? 0L : storage.downloadFailures())
            + ";storageOperationFailures=" + (storage == null ? 0L : storage.operationFailures())
            + ";storagePrimaryDegraded=" + (storage != null && storage.primaryStorageDegraded())
            + ";storagePrimaryFailures=" + (storage == null ? 0L : storage.primaryStorageFailures())
            + ";storageFallbackReads=" + (storage == null ? 0L : storage.fallbackReads())
            + ";storageFallbackWrites=" + (storage == null ? 0L : storage.fallbackWrites())
            + ";storageFallbackDeletes=" + (storage == null ? 0L : storage.fallbackDeletes())
            + ";storageFallbackOperations=" + (storage == null ? 0L : storage.fallbackOperations())
            + ";periodicSaveRetryQueue=" + (periodicSaveTask == null ? 0 : periodicSaveTask.retryQueueSize())
            + ";periodicSaveFailures=" + (periodicSaveTask == null ? 0L : periodicSaveTask.failuresTotal())
            + ";emptySaveRetryQueue=" + (emptyIslandSaveTask == null ? 0 : emptyIslandSaveTask.retryQueueSize())
            + ";emptySaveFailures=" + (emptyIslandSaveTask == null ? 0L : emptyIslandSaveTask.failuresTotal())
            + ";generatorKeys=" + (generatorListener == null ? 0 : generatorListener.generatorKeyCount())
            + ";generatorRuleLevels=" + (generatorListener == null ? 0 : generatorListener.ruleLevelCount())
            + ";generatorCacheTtlSeconds=" + (generatorListener == null ? 0L : generatorListener.cacheTtlSeconds())
            + ";proxySourceRejections=" + (routeSessionListener == null ? 0L : routeSessionListener.proxySourceRejections())
            + ";forwardingRejections=" + (routeSessionListener == null ? 0L : routeSessionListener.forwardingRejections())
            + ";routeSessionRejections=" + (routeSessionListener == null ? 0L : routeSessionListener.routeSessionRejections())
            + ";routeSessionCheckFailures=" + (routeSessionListener == null ? 0L : routeSessionListener.routeSessionCheckFailures())
            + ";hideNodeNames=" + configBoolean("routing.hide-node-names", true)
            + ";playerTopologyPolicy=logical-island-only"
            + ";playerNodeNamePolicy=" + (configBoolean("routing.hide-node-names", true) ? "hidden-from-player-routing-messages" : "visible-risk-admin-debug-only")
            + ";topologyExposureRisk=" + !configBoolean("routing.hide-node-names", true)
            + ";defaultNodeIdentityRisk=" + defaultNodeIdentityRisk(parseAgentRole(getConfig().getString("node.role", "ISLAND_NODE")), getConfig().getString("node.id", "island-1"), getConfig().getString("node.velocity-server-name", getConfig().getString("node.id", "island-1")))
            + ";chatBroadcasts=" + (permissionEventPoller == null ? 0L : permissionEventPoller.chatBroadcasts())
            + ";chatDeliveries=" + (permissionEventPoller == null ? 0L : permissionEventPoller.chatDeliveries())
            + ";chatNoRecipientBroadcasts=" + (permissionEventPoller == null ? 0L : permissionEventPoller.chatNoRecipientBroadcasts());
    }

    private void startIslandNodeWorker(CoreApiClient client, String nodeId, IslandStorage storage, IslandLimitCache limitCache) {
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            getConfig().getString("island-node.shard-world-prefix", "ci_shard_"),
            getConfig().getInt("island-node.shard-count", 4),
            getConfig().getInt("island-node.cell-size", 1024)
        );
        this.activeIslands = new ActiveIslandRegistry();
        agent.routeTickets().setActiveIslands(activeIslands);
        IslandSaveService saveService = new IslandSaveService(storage, new ExternalTarIslandBundleExporter(getServer().getWorldContainer().toPath()), getDataFolder().toPath().resolve("exports"), snapshotRetentionPolicy());
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(storage, shardWorldManager, agent.protection(), new IslandWorldRestorer(storage, getDataFolder().toPath().resolve("staging"), new BundleRestorePlanner(new ExternalTarBundleExtractor())), new ShardWorldPreloader(this), getConfig().getInt("island-node.activation.preload-radius", 4), new FileBackedCellTransfer(getServer().getWorldContainer().toPath()), activeIslands, saveService, getConfig().getInt("island-node.default-island-size", 300));
        IslandDeactivationHandler deactivationHandler = new IslandDeactivationHandler(activeIslands, shardWorldManager, agent.protection(), saveService);
        PermissionCacheSyncService permissionSync = new PermissionCacheSyncService(this, client, agent.permissionCache());
        this.jobWorker = new PaperIslandJobWorker(this, new CoreBackedIslandJobSource(client), activationHandler, deactivationHandler, activeIslands, permissionSync, nodeId);
        this.permissionEventPoller = new PermissionEventPoller(this, client, permissionSync, generatorLevels, cropGrowthLevels, limitCache, agent.protection(), nodeId, fallbackServerName, messages);
        this.periodicSaveTask = new PeriodicIslandSaveTask(this, activeIslands, saveService, client, nodeId);
        this.emptyIslandSaveTask = new EmptyIslandSaveTask(this, activeIslands, agent.protection(), saveService, client);
        this.periodicLevelScanTask = new PeriodicIslandLevelScanTask(this, activeIslands, new IslandLevelScanService(this, () -> activeIslands, client));
        permissionEventPoller.start(getConfig().getLong("protection.cache-event-poll-ticks", 100L));
        jobWorker.start(getConfig().getLong("island-node.activation.worker-interval-ticks", 20L));
        periodicSaveTask.start(getConfig().getLong("island-node.activation.periodic-save-seconds", 600L));
        emptyIslandSaveTask.start(getConfig().getLong("island-node.activation.save-on-empty-after-seconds", 300L));
        periodicLevelScanTask.start(getConfig().getLong("island-node.level-scan-interval-seconds", 900L));
    }

    private String coreApiToken() {
        String envToken = System.getenv("CI_CORE_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken;
        }
        return setupString("setup.core-api.auth-token", "core-api.auth-token", "");
    }

    private String coreAdminToken() {
        String envToken = System.getenv("CI_ADMIN_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken;
        }
        return setupString("setup.core-api.admin-token", "core-api.admin-token", "");
    }

    private String coreApiBaseUrl() {
        return setupString("setup.core-api.base-url", "core-api.base-url", "https://core-api.internal:8443");
    }

    private long coreApiTimeoutMillis() {
        long setupTimeout = getConfig().getLong("setup.core-api.timeout-ms", 0L);
        long timeout = setupTimeout > 0L ? setupTimeout : getConfig().getLong("core-api.timeout-ms", 3000L);
        return Math.max(1L, timeout);
    }

    private String setupString(String setupPath, String legacyPath, String fallback) {
        String value = getConfig().getString(setupPath, "");
        if (value == null || value.isBlank()) {
            value = getConfig().getString(legacyPath, fallback);
        }
        return resolveEnv(value);
    }

    private void logSecurityPosture() {
        if (configBoolean("security.require-velocity-forwarding", true)) {
            String forwardingSecret = resolveEnv(getConfig().getString("security.forwarding-secret", ""));
            if (forwardingSecret.isBlank()) {
                getLogger().warning("CloudIslands security: Velocity forwarding is required but security.forwarding-secret is empty");
            }
        }
        if (!configBoolean("security.enforce-route-session", true)) {
            getLogger().warning("CloudIslands security: route session enforcement is disabled");
        }
        if (configBoolean("security.allow-bungee-connect-plugin-messaging", false)) {
            getLogger().warning("CloudIslands security: BungeeCord connect plugin messaging is enabled; keep it disabled unless proxy fallback transfers require it");
        }
        if (getConfig().getStringList("security.proxy-source-allowlist").isEmpty()) {
            getLogger().warning("CloudIslands security: proxy source allowlist is empty; use a host firewall or set security.proxy-source-allowlist to Velocity source IPs");
        }
        if (coreApiToken().isBlank()) {
            getLogger().warning("CloudIslands security: core-api auth token is empty");
        }
    }

    private String resolveEnv(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }

    private boolean storageAvailable(IslandStorage storage) {
        if (storage == null) {
            return true;
        }
        try {
            return storage.available();
        } catch (Exception exception) {
            getLogger().warning("Island storage health check failed: " + exception.getMessage());
            return false;
        }
    }

    private SnapshotRetentionPolicy snapshotRetentionPolicy() {
        int hourly = getConfig().getInt("snapshots.keep-hourly", 24);
        int daily = getConfig().getInt("snapshots.keep-daily", 7);
        int weekly = getConfig().getInt("snapshots.keep-weekly", 4);
        int manual = getConfig().getInt("snapshots.keep-manual", 50);
        boolean compress = configBoolean("snapshots.compress", true);
        String checksum = getConfig().getString("snapshots.checksum", "SHA-256");
        return new SnapshotRetentionPolicy(hourly, daily, weekly, manual, compress, checksum).normalized();
    }

    private boolean configBoolean(String path, boolean fallback) {
        if (!getConfig().contains(path)) {
            return fallback;
        }
        Object raw = getConfig().get(path);
        if (raw instanceof Boolean value) {
            return value;
        }
        String normalized = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1") || normalized.equals("enable") || normalized.equals("enabled") || normalized.equals("켜기") || normalized.equals("허용") || normalized.equals("활성")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0") || normalized.equals("disable") || normalized.equals("disabled") || normalized.equals("끄기") || normalized.equals("거부") || normalized.equals("비활성")) {
            return false;
        }
        return fallback;
    }

    private Map<IslandPermission, String> denyMessages() {
        Map<IslandPermission, String> messages = new EnumMap<>(IslandPermission.class);
        var section = getConfig().getConfigurationSection("protection.deny-messages");
        if (section == null) {
            return messages;
        }
        for (String key : section.getKeys(false)) {
            try {
                IslandPermission permission = IslandPermission.valueOf(key.toUpperCase(Locale.ROOT).replace('-', '_'));
                String message = section.getString(key, "");
                if (message != null && !message.isBlank()) {
                    messages.put(permission, message);
                }
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Unknown protection deny message key: " + key);
            }
        }
        return messages;
    }
}
