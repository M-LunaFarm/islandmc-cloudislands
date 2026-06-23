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
import kr.lunaf.cloudislands.paper.gui.GuiSessions;
import kr.lunaf.cloudislands.paper.gui.IslandGuiMenuRegistrar;
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
import kr.lunaf.cloudislands.paper.platform.compatibility.PaperRuntimeCompatibility;
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
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    CloudIslandsPaperAgent agent;
    PaperIslandJobWorker jobWorker;
    PermissionEventPoller permissionEventPoller;
    PeriodicIslandSaveTask periodicSaveTask;
    EmptyIslandSaveTask emptyIslandSaveTask;
    PeriodicIslandLevelScanTask periodicLevelScanTask;
    ActiveIslandRegistry activeIslands;
    GeneratorLevelCache generatorLevels;
    IslandGeneratorListener generatorListener;
    PaperRouteSessionListener routeSessionListener;
    IslandBoundaryListener boundaryListener;
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

    @Override
    public void onEnable() {
        new PaperPluginBootstrap(this).enable();
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
        integrationRegistry = null;
        messages = null;
        if (playerLocales != null) {
            playerLocales.clear();
            playerLocales = null;
        }
        GuiSessions.clear();
        runtimeConfig = null;
        runtimeCompatibility = null;
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

    public PaperRuntimeConfig reloadRuntimeConfig() {
        runtimeConfig = loadRuntimeConfigSnapshot();
        return runtimeConfig();
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
