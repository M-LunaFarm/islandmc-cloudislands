package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import kr.lunaf.cloudislands.api.economy.EconomyBridge;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.config.ConfigSecretResolver;
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
import kr.lunaf.cloudislands.paper.bootstrap.PaperBootstrapStatus;
import kr.lunaf.cloudislands.paper.bootstrap.LifecycleRegistry;
import kr.lunaf.cloudislands.paper.bootstrap.PaperHealthRuntime;
import kr.lunaf.cloudislands.paper.bootstrap.PaperRuntimeServices;
import kr.lunaf.cloudislands.paper.cache.PermissionEventPoller;
import kr.lunaf.cloudislands.paper.cache.PermissionCacheSyncService;
import kr.lunaf.cloudislands.paper.cache.LocalCacheManager;
import kr.lunaf.cloudislands.paper.command.PaperCommandRegistrar;
import kr.lunaf.cloudislands.paper.command.PaperBootstrapStatusCommand;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfigLoader;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfigReloadResult;
import kr.lunaf.cloudislands.paper.generator.ConfigGeneratorRules;
import kr.lunaf.cloudislands.paper.generator.CropGrowthLevelCache;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.generator.IslandCropGrowthListener;
import kr.lunaf.cloudislands.paper.generator.IslandGeneratorListener;
import kr.lunaf.cloudislands.paper.gui.GuiSessions;
import kr.lunaf.cloudislands.paper.gui.IslandGuiMenuRegistrar;
import kr.lunaf.cloudislands.paper.integration.PaperIntegrationRegistry;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomBlockKeyService;
import kr.lunaf.cloudislands.paper.integration.stacker.StackAmountService;
import kr.lunaf.cloudislands.paper.integration.vanish.PlayerVisibilityService;
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
import kr.lunaf.cloudislands.paper.platform.compatibility.PaperRuntimeCompatibility;
import kr.lunaf.cloudislands.paper.redis.PaperRedisClient;
import kr.lunaf.cloudislands.paper.security.ProxySourceAllowlist;
import kr.lunaf.cloudislands.paper.session.PaperBrandingListener;
import kr.lunaf.cloudislands.paper.session.PaperChatListener;
import kr.lunaf.cloudislands.paper.session.PaperPlayerProfileListener;
import kr.lunaf.cloudislands.paper.session.PaperScoreboardListener;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import kr.lunaf.cloudislands.paper.session.PlayerLocaleCache;
import kr.lunaf.cloudislands.paper.session.PlayerFlightPreferenceRegistry;
import kr.lunaf.cloudislands.paper.session.TeamChatModeRegistry;
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
import org.bukkit.event.HandlerList;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    CloudIslandsPaperAgent agent;
    PaperIslandJobWorker jobWorker;
    PermissionEventPoller permissionEventPoller;
    PeriodicIslandSaveTask periodicSaveTask;
    EmptyIslandSaveTask emptyIslandSaveTask;
    PeriodicIslandLevelScanTask periodicLevelScanTask;
    IslandLevelScanService levelScanService;
    ActiveIslandRegistry activeIslands;
    GeneratorLevelCache generatorLevels;
    IslandGeneratorListener generatorListener;
    PaperRouteSessionListener routeSessionListener;
    IslandBoundaryListener boundaryListener;
    IslandPortalListener portalListener;
    MessageRenderer messages;
    PaperRedisClient redisClient;
    LocalCacheManager localCaches;
    ProxySourceAllowlist proxySourceAllowlist;
    MeteredIslandStorage islandStorage;
    PaperIntegrationRegistry integrationRegistry;
    LifecycleRegistry lifecycle;
    PlayerLocaleCache playerLocales;
    PaperRuntimeConfig runtimeConfig;
    PaperRuntimeCompatibility.RuntimeSelection runtimeCompatibility;
    PlayerVisibilityService playerVisibility;
    CustomBlockKeyService customBlockKeys;
    StackAmountService stackAmounts;
    AdminFlightOverrides adminFlightOverrides;
    PlayerFlightPreferenceRegistry playerFlightPreferences;
    PlayerIslandFlightService playerIslandFlightService;
    AdminChatSpyRegistry adminChatSpies;
    TeamChatModeRegistry teamChatModes;
    private final PaperBootstrapStatus bootstrapStatus = new PaperBootstrapStatus();
    private boolean bootstrapInProgress;

    @Override
    public void onEnable() {
        startRuntime();
    }

    @Override
    public void onDisable() {
        bootstrapInProgress = false;
        stopRuntimeState();
        bootstrapStatus.stopped();
    }

    public PaperBootstrapStatus.Snapshot bootstrapStatus() {
        return bootstrapStatus.snapshot();
    }

    public boolean retryBootstrap() {
        if (bootstrapInProgress || !bootstrapStatus.snapshot().retryable()) {
            return false;
        }
        stopRuntimeState();
        return startRuntime();
    }

    private boolean startRuntime() {
        if (bootstrapInProgress) {
            return false;
        }
        bootstrapInProgress = true;
        bootstrapStatus.starting();
        PaperBootstrapStatusCommand.install(this, bootstrapStatus);
        try {
            new PaperPluginBootstrap(this).enable();
            bootstrapStatus.ready();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            PaperBootstrapStatus.Snapshot failed = bootstrapStatus.failed(failure);
            stopRuntimeState();
            PaperBootstrapStatusCommand.install(this, bootstrapStatus);
            getLogger().warning("CloudIslands Paper entered bootstrap diagnostic mode after "
                + failed.failureType() + ": " + failed.failureMessage());
            getLogger().warning("Correct the startup problem and run /ciadmin retry; gameplay listeners and runtime services were rolled back.");
            return false;
        } finally {
            bootstrapInProgress = false;
        }
    }

    private void stopRuntimeState() {
        if (lifecycle != null) {
            LifecycleRegistry current = lifecycle;
            lifecycle = null;
            cleanup("lifecycle registry", current::close);
        }
        cleanup("Bukkit event handlers", () -> HandlerList.unregisterAll(this));
        cleanup("Bukkit scheduler tasks", () -> getServer().getScheduler().cancelTasks(this));
        cleanup("plugin messaging channels", () -> getServer().getMessenger().unregisterOutgoingPluginChannel(this));
        jobWorker = null;
        permissionEventPoller = null;
        periodicSaveTask = null;
        emptyIslandSaveTask = null;
        periodicLevelScanTask = null;
        levelScanService = null;
        activeIslands = null;
        if (redisClient != null) {
            PaperRedisClient current = redisClient;
            redisClient = null;
            cleanup("Redis client", current::close);
        }
        islandStorage = null;
        generatorLevels = null;
        generatorListener = null;
        routeSessionListener = null;
        boundaryListener = null;
        portalListener = null;
        proxySourceAllowlist = null;
        if (localCaches != null) {
            LocalCacheManager current = localCaches;
            localCaches = null;
            cleanup("local caches", current::invalidateAll);
        }
        integrationRegistry = null;
        playerVisibility = null;
        customBlockKeys = null;
        stackAmounts = null;
        if (playerLocales != null) {
            PlayerLocaleCache current = playerLocales;
            playerLocales = null;
            cleanup("player locale cache", current::clear);
        }
        if (playerIslandFlightService != null) {
            PlayerIslandFlightService current = playerIslandFlightService;
            playerIslandFlightService = null;
            cleanup("player flight state", () -> current.clearAll(getServer().getOnlinePlayers()));
        }
        playerFlightPreferences = null;
        if (adminFlightOverrides != null) {
            cleanup("admin flight overrides", () -> adminFlightOverrides.clearAll());
            adminFlightOverrides = null;
        }
        if (adminChatSpies != null) {
            cleanup("admin chat spies", () -> adminChatSpies.clearAll());
            adminChatSpies = null;
        }
        if (teamChatModes != null) {
            TeamChatModeRegistry current = teamChatModes;
            teamChatModes = null;
            cleanup("team chat modes", current::clearAll);
        }
        GuiSessions.clear();
        kr.lunaf.cloudislands.paper.command.AddonIslandCommandRegistry.global().clear();
        agent = null;
        messages = null;
        runtimeConfig = null;
        runtimeCompatibility = null;
    }

    private void cleanup(String component, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException | LinkageError failure) {
            getLogger().warning("CloudIslands cleanup failed for " + component + ": " + PaperBootstrapStatus.sanitize(failure.getMessage()));
        }
    }

    public CloudIslandsPaperAgent agent() {
        return agent;
    }

    public TeamChatModeRegistry teamChatModes() {
        return teamChatModes;
    }

    public PlayerIslandFlightService playerIslandFlightService() {
        return playerIslandFlightService;
    }

    public ActiveIslandRegistry activeIslands() {
        return activeIslands;
    }

    public PlayerVisibilityService playerVisibility() {
        return playerVisibility == null ? PlayerVisibilityService.metadataOnly() : playerVisibility;
    }

    public CustomBlockKeyService customBlockKeys() {
        return customBlockKeys == null ? CustomBlockKeyService.vanillaOnly() : customBlockKeys;
    }

    public StackAmountService stackAmounts() {
        return stackAmounts == null ? StackAmountService.physicalOnly() : stackAmounts;
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

    public IslandLevelScanService levelScanService() {
        return levelScanService;
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

    IslandPortalListener portalListener() {
        return portalListener;
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

    public PaperIntegrationRegistry integrationRegistry() {
        if (integrationRegistry == null) {
            integrationRegistry = PaperIntegrationRegistry.discover(getServer());
        }
        return integrationRegistry;
    }

    public PaperRuntimeConfig runtimeConfig() {
        return runtimeConfig == null ? PaperRuntimeConfig.defaults() : runtimeConfig;
    }

    public PaperRuntimeCompatibility.RuntimeSelection runtimeCompatibility() {
        return runtimeCompatibility;
    }

    public AdminFlightOverrides adminFlightOverrides() {
        if (adminFlightOverrides == null) {
            adminFlightOverrides = new AdminFlightOverrides();
        }
        return adminFlightOverrides;
    }

    public AdminChatSpyRegistry adminChatSpies() {
        if (adminChatSpies == null) {
            adminChatSpies = new AdminChatSpyRegistry();
        }
        return adminChatSpies;
    }

    public synchronized PaperRuntimeConfigReloadResult reloadRuntimeConfig() {
        return applyRuntimeConfigSnapshot(loadRuntimeConfigSnapshot());
    }

    public synchronized PaperRuntimeConfigReloadResult applyRuntimeConfigSnapshot(PaperRuntimeConfig candidate) {
        PaperRuntimeConfigReloadResult result = PaperRuntimeConfigReloadResult.analyze(runtimeConfig(), candidate);
        if (!result.restartRequiredChanges().isEmpty()) {
            return result;
        }
        TranslationManager translations = TranslationManager.fromSnapshot(candidate.messages(), candidate.serviceName());
        if (messages == null) {
            messages = new MessageRenderer(translations);
        } else {
            messages.reload(translations);
        }
        runtimeConfig = candidate;
        return result.appliedResult();
    }

    public PaperRuntimeConfig loadRuntimeConfigSnapshot() {
        return PaperRuntimeConfigLoader.load(this, this::resolveEnv);
    }

    boolean guiEnabledForRole(AgentRole role) {
        return runtimeConfig().guiEnabledForRole(role);
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

    boolean defaultNodeIdentityRisk(AgentRole role, String nodeId, String velocityServerName) {
        if (role != AgentRole.ISLAND_NODE) {
            return false;
        }
        String safeNodeId = nodeId == null ? "" : nodeId.trim();
        String safeVelocityServerName = velocityServerName == null ? "" : velocityServerName.trim();
        return safeNodeId.equalsIgnoreCase("island-1") || safeVelocityServerName.equalsIgnoreCase("Island-1");
    }

    String resolveEnv(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if ((trimmed.startsWith("${env:") || trimmed.startsWith("${file:")) && trimmed.endsWith("}")) {
            ConfigSecretResolver.ResolvedSecret resolved = ConfigSecretResolver.resolve(trimmed, System.getenv()::get, getDataFolder().toPath());
            if (!resolved.resolved()) {
                getLogger().warning("Could not resolve config secret reference " + resolved.issue().path() + " (" + resolved.issue().code() + ").");
            }
            return resolved.value().trim();
        }
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }

}
