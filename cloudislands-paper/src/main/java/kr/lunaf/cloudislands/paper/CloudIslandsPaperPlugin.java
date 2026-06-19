package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.EmptyIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.IslandDeactivationHandler;
import kr.lunaf.cloudislands.paper.activation.IslandSaveService;
import kr.lunaf.cloudislands.paper.activation.PeriodicIslandSaveTask;
import kr.lunaf.cloudislands.paper.activation.ShardWorldManager;
import kr.lunaf.cloudislands.paper.bootstrap.PaperHeartbeatRuntime;
import kr.lunaf.cloudislands.paper.bootstrap.LifecycleRegistry;
import kr.lunaf.cloudislands.paper.bootstrap.PaperHealthRuntime;
import kr.lunaf.cloudislands.paper.bootstrap.PaperRuntimeServices;
import kr.lunaf.cloudislands.paper.cache.PermissionEventPoller;
import kr.lunaf.cloudislands.paper.cache.PermissionCacheSyncService;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.command.PaperCommandRegistrar;
import kr.lunaf.cloudislands.paper.generator.ConfigGeneratorRules;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.generator.IslandCropGrowthListener;
import kr.lunaf.cloudislands.paper.generator.IslandGeneratorListener;
import kr.lunaf.cloudislands.paper.gui.IslandGuiMenuRegistrar;
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
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    private CloudIslandsPaperAgent agent;
    private PaperIslandJobWorker jobWorker;
    private PermissionEventPoller permissionEventPoller;
    private PeriodicIslandSaveTask periodicSaveTask;
    private EmptyIslandSaveTask emptyIslandSaveTask;
    private PeriodicIslandLevelScanTask periodicLevelScanTask;
    private ActiveIslandRegistry activeIslands;
    private GeneratorLevelCache generatorLevels;
    private IslandGeneratorListener generatorListener;
    private PaperRouteSessionListener routeSessionListener;
    private IslandBoundaryListener boundaryListener;
    private MessageRenderer messages;
    private PaperRedisClient redisClient;
    private LocalCacheManager localCaches;
    private ProxySourceAllowlist proxySourceAllowlist;
    private MeteredIslandStorage islandStorage;
    private LifecycleRegistry lifecycle;

    @Override
    public void onEnable() {
        this.lifecycle = new LifecycleRegistry(getLogger());
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
        if (rejectDefaultNodeIdentity(role, nodeId, velocityServerName)) {
            return;
        }
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
        PaperRuntimeServices runtimeServices = PaperRuntimeServices.register(this, client, agent);
        lifecycle.started("runtime-services", runtimeServices::stop);
        EconomyBridge economyBridge = runtimeServices.economyBridge();
        IslandLimitCache limitCache = new IslandLimitCache(client);
        localCaches.register("limits", limitCache::invalidateAll);
        long denyMessageCooldownMs = getConfig().getLong("protection.deny-message-cooldown-ms", 1000L);
        BlockDeltaReporter blockDeltas = new BlockDeltaReporter(this, client);
        getServer().getPluginManager().registerEvents(new PaperPlayerProfileListener(client), this);
        getServer().getPluginManager().registerEvents(new PaperBrandingListener(this, messages), this);
        getServer().getPluginManager().registerEvents(new PaperChatListener(messages), this);
        getServer().getPluginManager().registerEvents(new PaperScoreboardListener(this, messages), this);
        if (guiEnabledForRole(role)) {
            IslandGuiMenuRegistrar.register(this, messages);
        }
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
        boolean requireProxySourceAllowlist = role == AgentRole.ISLAND_NODE && configBoolean("security.require-proxy-source-allowlist", true);
        this.routeSessionListener = new PaperRouteSessionListener(this, client, agent.routeTickets(), nodeId, requireRouteSession, forwardingReady, requireProxySourceAllowlist, fallbackServerName, proxySourceAllowlist, messages);
        getServer().getPluginManager().registerEvents(routeSessionListener, this);
        int routeWaitSeconds = getConfig().getInt("routing.wait-for-activation-timeout-seconds", 20);
        new PaperCommandRegistrar(this).register(agent, client, nodeId, routeWaitSeconds, fallbackServerName, economyBridge, messages, localCaches, () -> activeIslands);
        MeteredIslandStorage storage = role == AgentRole.ISLAND_NODE ? new MeteredIslandStorage(PaperStorageFactory.create(this, getConfig()), PaperStorageFactory.backendName(getConfig())) : null;
        this.islandStorage = storage;
        String supportedTemplates = String.join(",", getConfig().getStringList("node.supported-templates"));
        if (supportedTemplates.isBlank()) {
            supportedTemplates = getConfig().getString("node.supported-template", "*");
        }
        String templateVersions = templateVersionsMetadata();
        String heartbeatSupportedTemplates = templateVersions.isBlank() ? supportedTemplates : supportedTemplates + ";templateVersions=" + templateVersions;
        PaperObservabilityFormatter observability = new PaperObservabilityFormatter(this);
        PaperHeartbeatRuntime heartbeatRuntime = PaperHeartbeatRuntime.start(
            this,
            client,
            nodeId,
            pool,
            velocityServerName,
            supportedTemplates,
            () -> observability.heartbeatMetadata(heartbeatSupportedTemplates, storage),
            () -> storageAvailable(storage),
            () -> activeIslands == null ? 0 : activeIslands.size(),
            () -> jobWorker == null ? 0 : jobWorker.activationQueue(),
            () -> jobWorker == null ? 0 : jobWorker.recentFailurePenalty()
        );
        lifecycle.started("heartbeat", heartbeatRuntime);
        PaperHealthRuntime healthRuntime = PaperHealthRuntime.startIfEnabled(
            this,
            () -> observability.healthJson(role, nodeId),
            () -> observability.metricsText(role, nodeId, storage)
        );
        if (healthRuntime != null) {
            lifecycle.started("health", healthRuntime);
        }
        if (role == AgentRole.ISLAND_NODE) {
            startIslandNodeWorker(client, nodeId, storage, limitCache);
        }
        getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    @Override
    public void onDisable() {
        if (lifecycle != null) {
            lifecycle.close();
            lifecycle = null;
        }
        jobWorker = null;
        permissionEventPoller = null;
        periodicSaveTask = null;
        emptyIslandSaveTask = null;
        periodicLevelScanTask = null;
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

    PermissionEventPoller permissionEventPoller() {
        return permissionEventPoller;
    }

    PeriodicIslandSaveTask periodicSaveTask() {
        return periodicSaveTask;
    }

    EmptyIslandSaveTask emptyIslandSaveTask() {
        return emptyIslandSaveTask;
    }

    PeriodicIslandLevelScanTask periodicLevelScanTask() {
        return periodicLevelScanTask;
    }

    IslandGeneratorListener generatorListener() {
        return generatorListener;
    }

    PaperRouteSessionListener routeSessionListener() {
        return routeSessionListener;
    }

    IslandBoundaryListener boundaryListener() {
        return boundaryListener;
    }

    PaperRedisClient redisClient() {
        return redisClient;
    }

    LocalCacheManager localCaches() {
        return localCaches;
    }

    ProxySourceAllowlist proxySourceAllowlist() {
        return proxySourceAllowlist;
    }

    MeteredIslandStorage islandStorage() {
        return islandStorage;
    }

    boolean guiEnabledForRole(AgentRole role) {
        if (!configBoolean("paper-gui.enabled", true)) {
            return false;
        }
        if (role == AgentRole.ISLAND_NODE) {
            return configBoolean("paper-gui.island-node-enabled", true);
        }
        return configBoolean("paper-gui.lobby-enabled", true);
    }

    AgentRole parseAgentRole(String configuredRole) {
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

    private boolean rejectDefaultNodeIdentity(AgentRole role, String nodeId, String velocityServerName) {
        if (!defaultNodeIdentityRisk(role, nodeId, velocityServerName)
            || !configBoolean("node.reject-default-identity", true)) {
            return false;
        }
        getLogger().severe("CloudIslands ISLAND_NODE refused to start with the default island-1/Island-1 identity. Set unique node.id and node.velocity-server-name for every Island node, or set node.reject-default-identity=false only for a single-node sandbox.");
        getServer().getPluginManager().disablePlugin(this);
        return true;
    }

    boolean defaultNodeIdentityRisk(AgentRole role, String nodeId, String velocityServerName) {
        if (role != AgentRole.ISLAND_NODE) {
            return false;
        }
        String safeNodeId = nodeId == null ? "" : nodeId.trim();
        String safeVelocityServerName = velocityServerName == null ? "" : velocityServerName.trim();
        return safeNodeId.equalsIgnoreCase("island-1") || safeVelocityServerName.equalsIgnoreCase("Island-1");
    }

    private String templateVersionsMetadata() {
        org.bukkit.configuration.ConfigurationSection section = getConfig().getConfigurationSection("node.template-versions");
        if (section == null) {
            return "";
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        for (String key : section.getKeys(false)) {
            String version = section.getString(key, "");
            if (key == null || key.isBlank() || version == null || version.isBlank()) {
                continue;
            }
            values.add(key.trim().replace(',', '_').replace(';', '_').replace(':', '_').replace('=', '_') + ":" + version.trim().replace(',', '_').replace(';', '_'));
        }
        return String.join(",", values);
    }

    private void startIslandNodeWorker(CoreApiClient client, String nodeId, IslandStorage storage, IslandLimitCache limitCache) {
        CropGrowthLevelCache cropGrowthLevels = new CropGrowthLevelCache(client);
        String fallbackServerName = getConfig().getString("routing.fallback-on-failure", "Lobby");
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            getConfig().getString("island-node.shard-world-prefix", "ci_shard_"),
            getConfig().getInt("island-node.shard-count", 16),
            getConfig().getInt("island-node.cell-size", 1024)
        );
        this.activeIslands = new ActiveIslandRegistry();
        agent.routeTickets().setActiveIslands(activeIslands);
        IslandSaveService saveService = new IslandSaveService(storage, new ExternalTarIslandBundleExporter(getServer().getWorldContainer().toPath()), getDataFolder().toPath().resolve("exports"), snapshotRetentionPolicy());
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(storage, shardWorldManager, agent.protection(), new IslandWorldRestorer(storage, getDataFolder().toPath().resolve("staging"), new BundleRestorePlanner(new ExternalTarBundleExtractor())), new ShardWorldPreloader(this), getConfig().getInt("island-node.activation.preload-radius", 4), new FileBackedCellTransfer(getServer().getWorldContainer().toPath()), activeIslands, saveService, getConfig().getInt("island-node.default-island-size", 300));
        IslandDeactivationHandler deactivationHandler = new IslandDeactivationHandler(activeIslands, shardWorldManager, agent.protection(), saveService);
        PermissionCacheSyncService permissionSync = new PermissionCacheSyncService(this, client, agent.permissionCache());
        this.jobWorker = new PaperIslandJobWorker(this, new CoreBackedIslandJobSource(client), activationHandler, deactivationHandler, activeIslands, permissionSync, nodeId);
        this.permissionEventPoller = new PermissionEventPoller(this, client, permissionSync, generatorLevels, cropGrowthLevels, limitCache, agent.protection(), nodeId, fallbackServerName, messages, agent.cacheInvalidator());
        this.periodicSaveTask = new PeriodicIslandSaveTask(this, activeIslands, saveService, client, nodeId);
        this.emptyIslandSaveTask = new EmptyIslandSaveTask(this, activeIslands, agent.protection(), saveService, client);
        this.periodicLevelScanTask = new PeriodicIslandLevelScanTask(this, activeIslands, new IslandLevelScanService(this, () -> activeIslands, client));
        permissionEventPoller.start(getConfig().getLong("protection.cache-event-poll-ticks", 100L));
        lifecycle.started("permission-event-poller", permissionEventPoller::stop);
        jobWorker.start(getConfig().getLong("island-node.activation.worker-interval-ticks", 20L));
        lifecycle.started("job-worker", jobWorker::stop);
        periodicSaveTask.start(getConfig().getLong("island-node.activation.periodic-save-seconds", 600L));
        lifecycle.started("periodic-save", periodicSaveTask::stop);
        emptyIslandSaveTask.start(getConfig().getLong("island-node.activation.save-on-empty-after-seconds", 300L));
        lifecycle.started("empty-save", emptyIslandSaveTask::stop);
        periodicLevelScanTask.start(getConfig().getLong("island-node.level-scan-interval-seconds", 900L));
        lifecycle.started("level-scan", periodicLevelScanTask::stop);
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

    String resolveEnv(String value) {
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

    boolean configBoolean(String path, boolean fallback) {
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
