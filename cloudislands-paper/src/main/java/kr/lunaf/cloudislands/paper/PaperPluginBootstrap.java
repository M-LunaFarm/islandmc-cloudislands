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


final class PaperPluginBootstrap {
    private final CloudIslandsPaperPlugin plugin;

    PaperPluginBootstrap(CloudIslandsPaperPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        plugin.lifecycle = new LifecycleRegistry(plugin.getLogger());
        plugin.saveDefaultConfig();
        logSecurityPosture();
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "minecraft:brand");
        if (plugin.configBoolean("security.allow-bungee-connect-plugin-messaging", false)) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }
        String nodeId = plugin.getConfig().getString("node.id", "island-1");
        String pool = plugin.getConfig().getString("node.pool", "island");
        String velocityServerName = plugin.getConfig().getString("node.velocity-server-name", nodeId);
        AgentRole role = plugin.parseAgentRole(plugin.getConfig().getString("node.role", "ISLAND_NODE"));
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
        plugin.agent = new CloudIslandsPaperAgent(plugin, role, client, nodeId);
        plugin.localCaches = new LocalCacheManager();
        plugin.localCaches.registerStats("permissions", plugin.agent.permissionCache()::invalidateAll, plugin.agent.permissionCache()::lookupCount, plugin.agent.permissionCache()::hitRatio);
        String serviceName = plugin.getConfig().getString("plugin.service-name", "CloudIslands");
        plugin.messages = new MessageRenderer(TranslationManager.fromConfig(plugin.getConfig(), serviceName));
        plugin.agent.routeTickets().setMessages(plugin.messages);
        plugin.redisClient = PaperRedisClient.create(
            plugin.resolveEnv(plugin.getConfig().getString("redis.uri", "redis://redis.internal:6379")),
            Duration.ofMillis(Math.max(1L, plugin.getConfig().getLong("redis.timeout-ms", 1000L)))
        );
        PaperRuntimeServices runtimeServices = PaperRuntimeServices.register(plugin, client, plugin.agent);
        plugin.lifecycle.started("runtime-services", runtimeServices::stop);
        EconomyBridge economyBridge = runtimeServices.economyBridge();
        IslandLimitCache limitCache = new IslandLimitCache(client);
        plugin.localCaches.register("limits", limitCache::invalidateAll);
        long denyMessageCooldownMs = plugin.getConfig().getLong("protection.deny-message-cooldown-ms", 1000L);
        BlockDeltaReporter blockDeltas = new BlockDeltaReporter(plugin, client);
        plugin.getServer().getPluginManager().registerEvents(new PaperPlayerProfileListener(client), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PaperBrandingListener(plugin, plugin.messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PaperChatListener(plugin.messages), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PaperScoreboardListener(plugin, plugin.messages), plugin);
        if (plugin.guiEnabledForRole(role)) {
            IslandGuiMenuRegistrar.register(plugin, plugin.messages);
        }
        if (role == AgentRole.ISLAND_NODE) {
            plugin.getServer().getPluginManager().registerEvents(new IslandProtectionListener(plugin.agent.protection(), blockDeltas, denyMessageCooldownMs, denyMessages()), plugin);
            plugin.boundaryListener = new IslandBoundaryListener(plugin.agent.protection(), plugin.messages);
            plugin.getServer().getPluginManager().registerEvents(plugin.boundaryListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(new IslandGameplayFlagListener(plugin.agent.protection(), plugin.messages), plugin);
            plugin.getServer().getPluginManager().registerEvents(new IslandLimitListener(plugin.agent.protection(), limitCache, plugin.messages), plugin);
            plugin.getServer().getPluginManager().registerEvents(new IslandEntityLimitListener(plugin.agent.protection(), limitCache, plugin.messages), plugin);
            plugin.generatorLevels = new GeneratorLevelCache(client, plugin.getConfig().getString("generators.default-key", "default"));
            CropGrowthLevelCache cropGrowthLevels = new CropGrowthLevelCache(client);
            plugin.localCaches.register("generator-levels", plugin.generatorLevels::invalidateAll);
            plugin.localCaches.register("crop-growth-levels", cropGrowthLevels::invalidateAll);
            plugin.generatorListener = new IslandGeneratorListener(plugin.agent.protection(), ConfigGeneratorRules.load(plugin), plugin.generatorLevels, blockDeltas);
            plugin.getServer().getPluginManager().registerEvents(plugin.generatorListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(new IslandCropGrowthListener(plugin.agent.protection(), cropGrowthLevels), plugin);
        }
        String fallbackServerName = plugin.getConfig().getString("routing.fallback-on-failure", "Lobby");
        boolean enforceRouteSession = role == AgentRole.ISLAND_NODE && plugin.configBoolean("security.enforce-route-session", true);
        boolean requireRouteSession = role == AgentRole.ISLAND_NODE && (plugin.configBoolean("routing.require-route-session", true) || enforceRouteSession);
        boolean forwardingReady = role != AgentRole.ISLAND_NODE
            || !plugin.configBoolean("security.require-velocity-forwarding", true)
            || !plugin.resolveEnv(plugin.getConfig().getString("security.forwarding-secret", "")).isBlank();
        plugin.proxySourceAllowlist = new ProxySourceAllowlist(plugin.getConfig().getStringList("security.proxy-source-allowlist"));
        boolean requireProxySourceAllowlist = role == AgentRole.ISLAND_NODE && plugin.configBoolean("security.require-proxy-source-allowlist", true);
        plugin.routeSessionListener = new PaperRouteSessionListener(plugin, client, plugin.agent.routeTickets(), nodeId, requireRouteSession, forwardingReady, requireProxySourceAllowlist, fallbackServerName, plugin.proxySourceAllowlist, plugin.messages);
        plugin.getServer().getPluginManager().registerEvents(plugin.routeSessionListener, plugin);
        int routeWaitSeconds = plugin.getConfig().getInt("routing.wait-for-activation-timeout-seconds", 20);
        new PaperCommandRegistrar(plugin).register(plugin.agent, client, nodeId, routeWaitSeconds, fallbackServerName, economyBridge, plugin.messages, plugin.localCaches, () -> plugin.activeIslands);
        MeteredIslandStorage storage = role == AgentRole.ISLAND_NODE ? new MeteredIslandStorage(PaperStorageFactory.create(plugin, plugin.getConfig()), PaperStorageFactory.backendName(plugin.getConfig())) : null;
        plugin.islandStorage = storage;
        String supportedTemplates = String.join(",", plugin.getConfig().getStringList("node.supported-templates"));
        if (supportedTemplates.isBlank()) {
            supportedTemplates = plugin.getConfig().getString("node.supported-template", "*");
        }
        String templateVersions = templateVersionsMetadata();
        String heartbeatSupportedTemplates = templateVersions.isBlank() ? supportedTemplates : supportedTemplates + ";templateVersions=" + templateVersions;
        PaperObservabilityFormatter observability = new PaperObservabilityFormatter(plugin);
        PaperHeartbeatRuntime heartbeatRuntime = PaperHeartbeatRuntime.start(
            plugin,
            client,
            nodeId,
            pool,
            velocityServerName,
            supportedTemplates,
            () -> observability.heartbeatMetadata(heartbeatSupportedTemplates, storage),
            () -> storageAvailable(storage),
            () -> plugin.activeIslands == null ? 0 : plugin.activeIslands.size(),
            () -> plugin.jobWorker == null ? 0 : plugin.jobWorker.activationQueue(),
            () -> plugin.jobWorker == null ? 0 : plugin.jobWorker.recentFailurePenalty()
        );
        plugin.lifecycle.started("heartbeat", heartbeatRuntime);
        PaperHealthRuntime healthRuntime = PaperHealthRuntime.startIfEnabled(
            plugin,
            () -> observability.healthJson(role, nodeId),
            () -> observability.metricsText(role, nodeId, storage)
        );
        if (healthRuntime != null) {
            plugin.lifecycle.started("health", healthRuntime);
        }
        if (role == AgentRole.ISLAND_NODE) {
            startIslandNodeWorker(client, nodeId, storage, limitCache);
        }
        plugin.getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    private void warnIfDefaultNodeIdentity(AgentRole role, String nodeId, String velocityServerName) {
        if (!plugin.defaultNodeIdentityRisk(role, nodeId, velocityServerName)) {
            return;
        }
        plugin.getLogger().warning("CloudIslands node.id/velocity-server-name still use the default island-1/Island-1 identity. Set a unique node.id and Velocity server name for every Island node before running multiple Island servers.");
    }

    private boolean rejectDefaultNodeIdentity(AgentRole role, String nodeId, String velocityServerName) {
        if (!plugin.defaultNodeIdentityRisk(role, nodeId, velocityServerName)
            || !plugin.configBoolean("node.reject-default-identity", true)) {
            return false;
        }
        plugin.getLogger().severe("CloudIslands ISLAND_NODE refused to start with the default island-1/Island-1 identity. Set unique node.id and node.velocity-server-name for every Island node, or set node.reject-default-identity=false only for a single-node sandbox.");
        plugin.getServer().getPluginManager().disablePlugin(plugin);
        return true;
    }

    private String templateVersionsMetadata() {
        org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("node.template-versions");
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
        String fallbackServerName = plugin.getConfig().getString("routing.fallback-on-failure", "Lobby");
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            plugin.getConfig().getString("island-node.shard-world-prefix", "ci_shard_"),
            plugin.getConfig().getInt("island-node.shard-count", 16),
            plugin.getConfig().getInt("island-node.cell-size", 1024)
        );
        plugin.activeIslands = new ActiveIslandRegistry();
        plugin.agent.routeTickets().setActiveIslands(plugin.activeIslands);
        IslandSaveService saveService = new IslandSaveService(storage, new ExternalTarIslandBundleExporter(plugin.getServer().getWorldContainer().toPath()), plugin.getDataFolder().toPath().resolve("exports"), snapshotRetentionPolicy());
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(storage, shardWorldManager, plugin.agent.protection(), new IslandWorldRestorer(storage, plugin.getDataFolder().toPath().resolve("staging"), new BundleRestorePlanner(new ExternalTarBundleExtractor())), new ShardWorldPreloader(plugin), plugin.getConfig().getInt("island-node.activation.preload-radius", 4), new FileBackedCellTransfer(plugin.getServer().getWorldContainer().toPath()), plugin.activeIslands, saveService, plugin.getConfig().getInt("island-node.default-island-size", 300));
        IslandDeactivationHandler deactivationHandler = new IslandDeactivationHandler(plugin.activeIslands, shardWorldManager, plugin.agent.protection(), saveService);
        PermissionCacheSyncService permissionSync = new PermissionCacheSyncService(plugin, client, plugin.agent.permissionCache());
        plugin.jobWorker = new PaperIslandJobWorker(plugin, new CoreBackedIslandJobSource(client), activationHandler, deactivationHandler, plugin.activeIslands, permissionSync, nodeId);
        plugin.permissionEventPoller = new PermissionEventPoller(plugin, client, permissionSync, plugin.generatorLevels, cropGrowthLevels, limitCache, plugin.agent.protection(), nodeId, fallbackServerName, plugin.messages, plugin.agent.cacheInvalidator());
        plugin.periodicSaveTask = new PeriodicIslandSaveTask(plugin, plugin.activeIslands, saveService, client, nodeId);
        plugin.emptyIslandSaveTask = new EmptyIslandSaveTask(plugin, plugin.activeIslands, plugin.agent.protection(), saveService, client);
        plugin.periodicLevelScanTask = new PeriodicIslandLevelScanTask(plugin, plugin.activeIslands, new IslandLevelScanService(plugin, () -> plugin.activeIslands, client));
        plugin.permissionEventPoller.start(plugin.getConfig().getLong("protection.cache-event-poll-ticks", 100L));
        plugin.lifecycle.started("permission-event-poller", plugin.permissionEventPoller::stop);
        plugin.jobWorker.start(plugin.getConfig().getLong("island-node.activation.worker-interval-ticks", 20L));
        plugin.lifecycle.started("job-worker", plugin.jobWorker::stop);
        plugin.periodicSaveTask.start(plugin.getConfig().getLong("island-node.activation.periodic-save-seconds", 600L));
        plugin.lifecycle.started("periodic-save", plugin.periodicSaveTask::stop);
        plugin.emptyIslandSaveTask.start(plugin.getConfig().getLong("island-node.activation.save-on-empty-after-seconds", 300L));
        plugin.lifecycle.started("empty-save", plugin.emptyIslandSaveTask::stop);
        plugin.periodicLevelScanTask.start(plugin.getConfig().getLong("island-node.level-scan-interval-seconds", 900L));
        plugin.lifecycle.started("level-scan", plugin.periodicLevelScanTask::stop);
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
        long setupTimeout = plugin.getConfig().getLong("setup.core-api.timeout-ms", 0L);
        long timeout = setupTimeout > 0L ? setupTimeout : plugin.getConfig().getLong("core-api.timeout-ms", 3000L);
        return Math.max(1L, timeout);
    }

    private String setupString(String setupPath, String legacyPath, String fallback) {
        String value = plugin.getConfig().getString(setupPath, "");
        if (value == null || value.isBlank()) {
            value = plugin.getConfig().getString(legacyPath, fallback);
        }
        return plugin.resolveEnv(value);
    }

    private void logSecurityPosture() {
        if (plugin.configBoolean("security.require-velocity-forwarding", true)) {
            String forwardingSecret = plugin.resolveEnv(plugin.getConfig().getString("security.forwarding-secret", ""));
            if (forwardingSecret.isBlank()) {
                plugin.getLogger().warning("CloudIslands security: Velocity forwarding is required but security.forwarding-secret is empty");
            }
        }
        if (!plugin.configBoolean("security.enforce-route-session", true)) {
            plugin.getLogger().warning("CloudIslands security: route session enforcement is disabled");
        }
        if (plugin.configBoolean("security.allow-bungee-connect-plugin-messaging", false)) {
            plugin.getLogger().warning("CloudIslands security: BungeeCord connect plugin messaging is enabled; keep it disabled unless proxy fallback transfers require it");
        }
        if (plugin.getConfig().getStringList("security.proxy-source-allowlist").isEmpty()) {
            plugin.getLogger().warning("CloudIslands security: proxy source allowlist is empty; use a host firewall or set security.proxy-source-allowlist to Velocity source IPs");
        }
        if (coreApiToken().isBlank()) {
            plugin.getLogger().warning("CloudIslands security: core-api auth token is empty");
        }
    }

    private boolean storageAvailable(IslandStorage storage) {
        if (storage == null) {
            return true;
        }
        try {
            return storage.available();
        } catch (Exception exception) {
            plugin.getLogger().warning("Island storage health check failed: " + exception.getMessage());
            return false;
        }
    }

    private SnapshotRetentionPolicy snapshotRetentionPolicy() {
        int hourly = plugin.getConfig().getInt("snapshots.keep-hourly", 24);
        int daily = plugin.getConfig().getInt("snapshots.keep-daily", 7);
        int weekly = plugin.getConfig().getInt("snapshots.keep-weekly", 4);
        int manual = plugin.getConfig().getInt("snapshots.keep-manual", 50);
        boolean compress = plugin.configBoolean("snapshots.compress", true);
        String checksum = plugin.getConfig().getString("snapshots.checksum", "SHA-256");
        return new SnapshotRetentionPolicy(hourly, daily, weekly, manual, compress, checksum).normalized();
    }

    private Map<IslandPermission, String> denyMessages() {
        Map<IslandPermission, String> messages = new EnumMap<>(IslandPermission.class);
        var section = plugin.getConfig().getConfigurationSection("protection.deny-messages");
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
                plugin.getLogger().warning("Unknown protection deny message key: " + key);
            }
        }
        return messages;
    }
}
