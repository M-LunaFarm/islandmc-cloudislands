package kr.lunaf.cloudislands.paper;

import java.net.URI;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
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
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfigLoader;
import kr.lunaf.cloudislands.paper.generator.ConfigGeneratorRules;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.generator.IslandCropGrowthListener;
import kr.lunaf.cloudislands.paper.generator.IslandGeneratorListener;
import kr.lunaf.cloudislands.paper.gui.GuiActionExecutor;
import kr.lunaf.cloudislands.paper.gui.IslandGuiMenuRegistrar;
import kr.lunaf.cloudislands.paper.integration.IntegrationLifecycleHooks;
import kr.lunaf.cloudislands.paper.integration.PaperIntegrationRegistry;
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
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.paper.storage.MeteredIslandStorage;
import kr.lunaf.cloudislands.paper.storage.PaperStorageFactory;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.paper.world.bundle.ExternalTarBundleExtractor;
import kr.lunaf.cloudislands.paper.world.ShardWorldPreloader;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.export.ExternalTarIslandBundleExporter;
import kr.lunaf.cloudislands.storage.IslandStorage;
import org.bukkit.plugin.java.JavaPlugin;


final class PaperPluginBootstrap {
    private final CloudIslandsPaperPlugin plugin;

    PaperPluginBootstrap(CloudIslandsPaperPlugin plugin) {
        this.plugin = plugin;
    }

    void enable() {
        plugin.lifecycle = new LifecycleRegistry(plugin.getLogger());
        PaperRuntimeConfig config = PaperRuntimeConfigLoader.load(plugin, plugin::resolveEnv);
        plugin.runtimeConfig = config;
        logSecurityPosture(config);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "minecraft:brand");
        if (config.security().allowBungeeConnectPluginMessaging()) {
            plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, "BungeeCord");
        }
        String nodeId = config.node().id();
        String velocityServerName = config.node().velocityServerName();
        AgentRole role = config.node().role();
        if (rejectDefaultNodeIdentity(role, nodeId, velocityServerName, config.node().rejectDefaultIdentity())) {
            return;
        }
        warnIfDefaultNodeIdentity(role, nodeId, velocityServerName);
        CoreApiClient client = new JdkCoreApiClient(
            URI.create(config.coreApi().baseUrl()),
            config.coreApi().token(),
            config.coreApi().adminToken(),
            config.coreApi().timeout()
        );
        plugin.agent = new CloudIslandsPaperAgent(plugin, role, client, nodeId);
        plugin.integrationRegistry = PaperIntegrationRegistry.discover(plugin.getServer());
        plugin.localCaches = new LocalCacheManager();
        plugin.localCaches.registerStats("permissions", plugin.agent.permissionCache()::invalidateAll, plugin.agent.permissionCache()::lookupCount, plugin.agent.permissionCache()::hitRatio);
        plugin.messages = new MessageRenderer(TranslationManager.fromSnapshot(config.messages(), config.serviceName()));
        plugin.playerLocales = new PlayerLocaleCache();
        plugin.agent.routeTickets().setMessages(plugin.messages);
        plugin.redisClient = PaperRedisClient.create(
            config.redis().uri(),
            config.redis().timeout()
        );
        PaperRuntimeServices runtimeServices = PaperRuntimeServices.register(plugin, client, plugin.agent, config);
        plugin.lifecycle.started("runtime-services", runtimeServices::stop);
        EconomyBridge economyBridge = runtimeServices.economyBridge();
        IslandLimitCache limitCache = new IslandLimitCache(client);
        plugin.localCaches.register("limits", limitCache::invalidateAll);
        BlockDeltaReporter blockDeltas = new BlockDeltaReporter(plugin, client);
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new PaperPlayerProfileListener(client, plugin.playerLocales));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new PaperBrandingListener(plugin, plugin.messages, plugin.playerLocales));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new PaperChatListener(plugin.messages, plugin.playerLocales));
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new PaperScoreboardListener(plugin, plugin.messages, plugin.playerLocales));
        if (role == AgentRole.ISLAND_NODE) {
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandProtectionListener(plugin.agent.protection(), blockDeltas, config.protection().denyMessageCooldownMs(), config.protection().denyMessages()));
            plugin.boundaryListener = new IslandBoundaryListener(plugin.agent.protection(), plugin.messages);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, plugin.boundaryListener);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandGameplayFlagListener(plugin.agent.protection(), plugin.messages, plugin.playerLocales));
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandLimitListener(plugin.agent.protection(), limitCache, plugin.messages));
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandEntityLimitListener(plugin.agent.protection(), limitCache, plugin.messages));
            plugin.generatorLevels = new GeneratorLevelCache(client, config.generator().defaultKey());
            CropGrowthLevelCache cropGrowthLevels = new CropGrowthLevelCache(client);
            plugin.localCaches.register("generator-levels", plugin.generatorLevels::invalidateAll);
            plugin.localCaches.register("crop-growth-levels", cropGrowthLevels::invalidateAll);
            plugin.generatorListener = new IslandGeneratorListener(plugin.agent.protection(), ConfigGeneratorRules.load(plugin), plugin.generatorLevels, blockDeltas);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, plugin.generatorListener);
            kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, new IslandCropGrowthListener(plugin.agent.protection(), cropGrowthLevels));
        }
        String fallbackServerName = config.routing().fallbackServerName();
        boolean enforceRouteSession = role == AgentRole.ISLAND_NODE && config.security().enforceRouteSession();
        boolean requireRouteSession = role == AgentRole.ISLAND_NODE && (config.security().requireRouteSession() || enforceRouteSession);
        boolean forwardingReady = role != AgentRole.ISLAND_NODE
            || !config.security().requireVelocityForwarding()
            || !config.security().forwardingSecret().isBlank();
        plugin.proxySourceAllowlist = new ProxySourceAllowlist(config.security().proxySourceAllowlist());
        boolean requireProxySourceAllowlist = role == AgentRole.ISLAND_NODE && config.security().requireProxySourceAllowlist();
        plugin.routeSessionListener = new PaperRouteSessionListener(plugin, client, plugin.agent.routeTickets(), nodeId, requireRouteSession, forwardingReady, requireProxySourceAllowlist, fallbackServerName, plugin.proxySourceAllowlist, plugin.messages, plugin.playerLocales);
        kr.lunaf.cloudislands.paper.platform.event.PaperEvents.register(plugin, plugin.routeSessionListener);
        int routeWaitSeconds = config.routing().waitForActivationTimeoutSeconds();
        GuiActionExecutor guiActions = new PaperCommandRegistrar(plugin).register(plugin.agent, client, nodeId, routeWaitSeconds, fallbackServerName, economyBridge, plugin.messages, plugin.localCaches, plugin.playerLocales, () -> plugin.activeIslands);
        if (config.guiEnabledForRole(role)) {
            IslandGuiMenuRegistrar.register(plugin, plugin.messages, guiActions);
        }
        MeteredIslandStorage storage = role == AgentRole.ISLAND_NODE ? new MeteredIslandStorage(PaperStorageFactory.create(plugin, config.storage()), PaperStorageFactory.backendName(config.storage())) : null;
        plugin.islandStorage = storage;
        String supportedTemplates = config.node().supportedTemplatesCsv();
        String templateVersions = config.node().templateVersions();
        String heartbeatSupportedTemplates = templateVersions.isBlank() ? supportedTemplates : supportedTemplates + ";templateVersions=" + templateVersions;
        PaperObservabilityFormatter observability = new PaperObservabilityFormatter(plugin, config);
        PaperHeartbeatRuntime heartbeatRuntime = PaperHeartbeatRuntime.start(
            plugin,
            client,
            config,
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
            config.health(),
            () -> observability.healthJson(role, nodeId),
            () -> observability.metricsText(role, nodeId, storage)
        );
        if (healthRuntime != null) {
            plugin.lifecycle.started("health", healthRuntime);
        }
        if (role == AgentRole.ISLAND_NODE) {
            startIslandNodeWorker(client, nodeId, storage, limitCache, config);
        }
        plugin.getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    private void warnIfDefaultNodeIdentity(AgentRole role, String nodeId, String velocityServerName) {
        if (!plugin.defaultNodeIdentityRisk(role, nodeId, velocityServerName)) {
            return;
        }
        plugin.getLogger().warning("CloudIslands node.id/velocity-server-name still use the default island-1/Island-1 identity. Set a unique node.id and Velocity server name for every Island node before running multiple Island servers.");
    }

    private boolean rejectDefaultNodeIdentity(AgentRole role, String nodeId, String velocityServerName, boolean rejectDefaultIdentity) {
        if (!plugin.defaultNodeIdentityRisk(role, nodeId, velocityServerName)
            || !rejectDefaultIdentity) {
            return false;
        }
        plugin.getLogger().severe("CloudIslands ISLAND_NODE refused to start with the default island-1/Island-1 identity. Set unique node.id and node.velocity-server-name for every Island node, or set node.reject-default-identity=false only for a single-node sandbox.");
        plugin.getServer().getPluginManager().disablePlugin(plugin);
        return true;
    }

    private void startIslandNodeWorker(CoreApiClient client, String nodeId, IslandStorage storage, IslandLimitCache limitCache, PaperRuntimeConfig config) {
        CropGrowthLevelCache cropGrowthLevels = new CropGrowthLevelCache(client);
        String fallbackServerName = config.routing().fallbackServerName();
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            config.worker().shardWorldPrefix(),
            config.worker().shardCount(),
            config.worker().cellSize()
        );
        plugin.activeIslands = new ActiveIslandRegistry();
        plugin.agent.routeTickets().setActiveIslands(plugin.activeIslands);
        IntegrationLifecycleHooks integrationHooks = IntegrationLifecycleHooks.fromRegistry(plugin.integrationRegistry, nodeId);
        IslandSaveService saveService = new IslandSaveService(storage, new ExternalTarIslandBundleExporter(plugin.getServer().getWorldContainer().toPath(), integrationHooks), plugin.getDataFolder().toPath().resolve("exports"), config.snapshots());
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(storage, shardWorldManager, plugin.agent.protection(), new IslandWorldRestorer(storage, plugin.getDataFolder().toPath().resolve("staging"), new BundleRestorePlanner(new ExternalTarBundleExtractor()), integrationHooks), new ShardWorldPreloader(plugin), config.worker().activationPreloadRadius(), new FileBackedCellTransfer(plugin.getServer().getWorldContainer().toPath()), plugin.activeIslands, saveService, config.worker().defaultIslandSize());
        IslandDeactivationHandler deactivationHandler = new IslandDeactivationHandler(plugin.activeIslands, shardWorldManager, plugin.agent.protection(), saveService);
        PermissionCacheSyncService permissionSync = new PermissionCacheSyncService(plugin, client, plugin.agent.permissionCache());
        plugin.jobWorker = new PaperIslandJobWorker(plugin, new CoreBackedIslandJobSource(client), activationHandler, deactivationHandler, plugin.activeIslands, permissionSync, nodeId);
        plugin.permissionEventPoller = new PermissionEventPoller(plugin, client, permissionSync, plugin.generatorLevels, cropGrowthLevels, limitCache, plugin.agent.protection(), nodeId, fallbackServerName, plugin.messages, plugin.agent.cacheInvalidator());
        plugin.periodicSaveTask = new PeriodicIslandSaveTask(plugin, plugin.activeIslands, saveService, client, nodeId);
        plugin.emptyIslandSaveTask = new EmptyIslandSaveTask(plugin, plugin.activeIslands, plugin.agent.protection(), saveService, client);
        plugin.periodicLevelScanTask = new PeriodicIslandLevelScanTask(plugin, plugin.activeIslands, new IslandLevelScanService(plugin, () -> plugin.activeIslands, client));
        plugin.permissionEventPoller.start(config.protection().cacheEventPollTicks());
        plugin.lifecycle.started("permission-event-poller", plugin.permissionEventPoller::stop);
        plugin.jobWorker.start(config.worker().activationWorkerIntervalTicks());
        plugin.lifecycle.started("job-worker", plugin.jobWorker::stop);
        plugin.periodicSaveTask.start(config.worker().periodicSaveSeconds());
        plugin.lifecycle.started("periodic-save", plugin.periodicSaveTask::stop);
        plugin.emptyIslandSaveTask.start(config.worker().saveOnEmptyAfterSeconds());
        plugin.lifecycle.started("empty-save", plugin.emptyIslandSaveTask::stop);
        plugin.periodicLevelScanTask.start(config.worker().levelScanIntervalSeconds());
        plugin.lifecycle.started("level-scan", plugin.periodicLevelScanTask::stop);
    }

    private void logSecurityPosture(PaperRuntimeConfig config) {
        if (config.security().requireVelocityForwarding()) {
            if (config.security().forwardingSecret().isBlank()) {
                plugin.getLogger().warning("CloudIslands security: Velocity forwarding is required but security.forwarding-secret is empty");
            }
        }
        if (!config.security().enforceRouteSession()) {
            plugin.getLogger().warning("CloudIslands security: route session enforcement is disabled");
        }
        if (config.security().allowBungeeConnectPluginMessaging()) {
            plugin.getLogger().warning("CloudIslands security: BungeeCord connect plugin messaging is enabled; keep it disabled unless proxy fallback transfers require it");
        }
        if (config.security().proxySourceAllowlist().isEmpty()) {
            plugin.getLogger().warning("CloudIslands security: proxy source allowlist is empty; use a host firewall or set security.proxy-source-allowlist to Velocity source IPs");
        }
        if (config.coreApi().token().isBlank()) {
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

}
