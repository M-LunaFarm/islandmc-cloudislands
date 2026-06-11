package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.paper.api.PaperCloudIslandsApi;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.IslandDeactivationHandler;
import kr.lunaf.cloudislands.paper.activation.IslandSaveService;
import kr.lunaf.cloudislands.paper.activation.ShardWorldManager;
import kr.lunaf.cloudislands.paper.admin.AdminCommandController;
import kr.lunaf.cloudislands.paper.cache.PermissionEventPoller;
import kr.lunaf.cloudislands.paper.cache.PermissionCacheSyncService;
import kr.lunaf.cloudislands.paper.command.IslandCommandController;
import kr.lunaf.cloudislands.paper.generator.DefaultGeneratorRules;
import kr.lunaf.cloudislands.paper.generator.GeneratorLevelCache;
import kr.lunaf.cloudislands.paper.generator.IslandGeneratorListener;
import kr.lunaf.cloudislands.paper.gui.AdminNodeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBankMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBanMenu;
import kr.lunaf.cloudislands.paper.gui.IslandBiomeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandChatMenu;
import kr.lunaf.cloudislands.paper.gui.IslandCreateMenu;
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
import kr.lunaf.cloudislands.paper.gui.IslandSettingsMenu;
import kr.lunaf.cloudislands.paper.gui.IslandSnapshotMenu;
import kr.lunaf.cloudislands.paper.gui.IslandUpgradeMenu;
import kr.lunaf.cloudislands.paper.gui.IslandVisitMenu;
import kr.lunaf.cloudislands.paper.gui.IslandWarpMenu;
import kr.lunaf.cloudislands.paper.heartbeat.PaperHeartbeatService;
import kr.lunaf.cloudislands.paper.job.CoreBackedIslandJobSource;
import kr.lunaf.cloudislands.paper.job.PaperIslandJobWorker;
import kr.lunaf.cloudislands.paper.level.BlockDeltaReporter;
import kr.lunaf.cloudislands.paper.limit.IslandEntityLimitListener;
import kr.lunaf.cloudislands.paper.limit.IslandLimitCache;
import kr.lunaf.cloudislands.paper.limit.IslandLimitListener;
import kr.lunaf.cloudislands.paper.session.PaperPlayerProfileListener;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import kr.lunaf.cloudislands.paper.storage.PaperStorageFactory;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.paper.world.bundle.ExternalTarBundleExtractor;
import kr.lunaf.cloudislands.paper.world.ShardWorldPreloader;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.export.ExternalTarIslandBundleExporter;
import kr.lunaf.cloudislands.storage.IslandStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    private CloudIslandsPaperAgent agent;
    private PaperHeartbeatService heartbeatService;
    private PaperIslandJobWorker jobWorker;
    private PermissionEventPoller permissionEventPoller;
    private ActiveIslandRegistry activeIslands;
    private CloudIslandsApi api;
    private GeneratorLevelCache generatorLevels;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        String nodeId = getConfig().getString("node.id", "island-1");
        String pool = getConfig().getString("node.pool", "island");
        String velocityServerName = getConfig().getString("node.velocity-server-name", nodeId);
        AgentRole role = AgentRole.valueOf(getConfig().getString("node.role", "ISLAND_NODE"));
        CoreApiClient client = new JdkCoreApiClient(URI.create(getConfig().getString("core-api.base-url", "https://core-api.internal:8443")), System.getenv().getOrDefault("CI_CORE_TOKEN", ""), Duration.ofSeconds(3));
        this.agent = new CloudIslandsPaperAgent(this, role, client, nodeId);
        this.api = new PaperCloudIslandsApi(client, agent);
        getServer().getServicesManager().register(CloudIslandsApi.class, api, this, ServicePriority.Normal);
        IslandLimitCache limitCache = new IslandLimitCache(client);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(agent.protection(), new BlockDeltaReporter(this, client)), this);
        getServer().getPluginManager().registerEvents(new PaperPlayerProfileListener(client), this);
        getServer().getPluginManager().registerEvents(new IslandGameplayFlagListener(agent.protection()), this);
        getServer().getPluginManager().registerEvents(new IslandLimitListener(agent.protection(), limitCache), this);
        getServer().getPluginManager().registerEvents(new IslandEntityLimitListener(agent.protection(), limitCache), this);
        getServer().getPluginManager().registerEvents(new AdminNodeMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandBankMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandBanMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandBiomeMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandChatMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandCreateMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandFlagMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandHomeMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandInfoMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandInviteMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandLimitMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandLogMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandMainMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandMemberMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandMissionMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandMyIslandsMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandPermissionMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandRankingMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandSettingsMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandSnapshotMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandUpgradeMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandVisitMenu(), this);
        getServer().getPluginManager().registerEvents(new IslandWarpMenu(), this);
        this.generatorLevels = new GeneratorLevelCache(client);
        getServer().getPluginManager().registerEvents(new IslandGeneratorListener(agent.protection(), DefaultGeneratorRules.create(), generatorLevels), this);
        getServer().getPluginManager().registerEvents(new PaperRouteSessionListener(this, client, agent.routeTickets(), nodeId), this);
        PluginCommand admin = getCommand("ciadmin");
        if (admin != null) {
            AdminCommandController adminController = new AdminCommandController(agent, client, nodeId);
            admin.setExecutor(adminController);
            admin.setTabCompleter(adminController);
        }
        PluginCommand island = getCommand("island");
        if (island != null) {
            IslandCommandController islandController = new IslandCommandController(this, client, agent.protection());
            island.setExecutor(islandController);
            island.setTabCompleter(islandController);
        }
        IslandStorage storage = role == AgentRole.ISLAND_NODE ? PaperStorageFactory.create(this, getConfig()) : null;
        String supportedTemplates = String.join(",", getConfig().getStringList("node.supported-templates"));
        if (supportedTemplates.isBlank()) {
            supportedTemplates = getConfig().getString("node.supported-template", "*");
        }
        this.heartbeatService = new PaperHeartbeatService(this, client, nodeId, pool, velocityServerName, getDescription().getVersion(), supportedTemplates, () -> storageAvailable(storage));
        heartbeatService.start(getConfig().getLong("heartbeat.interval-ticks", 20L));
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
        if (heartbeatService != null) {
            heartbeatService.stop();
        }
        if (api != null) {
            getServer().getServicesManager().unregister(CloudIslandsApi.class, api);
            api = null;
        }
    }

    public CloudIslandsPaperAgent agent() {
        return agent;
    }

    public ActiveIslandRegistry activeIslands() {
        return activeIslands;
    }

    private void startIslandNodeWorker(CoreApiClient client, String nodeId, IslandStorage storage, IslandLimitCache limitCache) {
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            getConfig().getString("island-node.shard-world-prefix", "ci_shard_"),
            getConfig().getInt("island-node.shard-count", 4),
            getConfig().getInt("island-node.cell-size", 1024)
        );
        this.activeIslands = new ActiveIslandRegistry();
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(storage, shardWorldManager, agent.protection(), new IslandWorldRestorer(storage, getDataFolder().toPath().resolve("staging"), new BundleRestorePlanner(new ExternalTarBundleExtractor())), new ShardWorldPreloader(this), getConfig().getInt("island-node.activation.preload-radius", 4), new FileBackedCellTransfer(getServer().getWorldContainer().toPath()));
        IslandSaveService saveService = new IslandSaveService(storage, new ExternalTarIslandBundleExporter(getServer().getWorldContainer().toPath()), getDataFolder().toPath().resolve("exports"), getConfig().getInt("snapshots.keep-manual", 50));
        IslandDeactivationHandler deactivationHandler = new IslandDeactivationHandler(activeIslands, shardWorldManager, agent.protection(), saveService);
        PermissionCacheSyncService permissionSync = new PermissionCacheSyncService(this, client, agent.permissionCache());
        this.jobWorker = new PaperIslandJobWorker(this, new CoreBackedIslandJobSource(client), activationHandler, deactivationHandler, activeIslands, permissionSync, nodeId);
        this.permissionEventPoller = new PermissionEventPoller(this, client, permissionSync, generatorLevels, limitCache, nodeId);
        permissionEventPoller.start(getConfig().getLong("protection.cache-event-poll-ticks", 100L));
        jobWorker.start(getConfig().getLong("island-node.activation.worker-interval-ticks", 20L));
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
}
