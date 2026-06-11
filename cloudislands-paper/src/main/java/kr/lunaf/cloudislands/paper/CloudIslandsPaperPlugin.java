package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.activation.IslandActivationJobHandler;
import kr.lunaf.cloudislands.paper.activation.ShardWorldManager;
import kr.lunaf.cloudislands.paper.admin.AdminCommandController;
import kr.lunaf.cloudislands.paper.heartbeat.PaperHeartbeatService;
import kr.lunaf.cloudislands.paper.job.CoreBackedIslandJobSource;
import kr.lunaf.cloudislands.paper.job.PaperIslandJobWorker;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import kr.lunaf.cloudislands.paper.storage.PaperStorageFactory;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.ShardWorldPreloader;
import kr.lunaf.cloudislands.storage.IslandStorage;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    private CloudIslandsPaperAgent agent;
    private PaperHeartbeatService heartbeatService;
    private PaperIslandJobWorker jobWorker;
    private ActiveIslandRegistry activeIslands;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String nodeId = getConfig().getString("node.id", "island-1");
        String pool = getConfig().getString("node.pool", "island");
        String velocityServerName = getConfig().getString("node.velocity-server-name", nodeId);
        AgentRole role = AgentRole.valueOf(getConfig().getString("node.role", "ISLAND_NODE"));
        CoreApiClient client = new JdkCoreApiClient(URI.create(getConfig().getString("core-api.base-url", "https://core-api.internal:8443")), System.getenv().getOrDefault("CI_CORE_TOKEN", ""), Duration.ofSeconds(3));
        this.agent = new CloudIslandsPaperAgent(this, role, client, nodeId);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(agent.protection()), this);
        getServer().getPluginManager().registerEvents(new PaperRouteSessionListener(client, agent.routeTickets(), nodeId), this);
        PluginCommand admin = getCommand("ciadmin");
        if (admin != null) {
            admin.setExecutor(new AdminCommandController(agent));
        }
        this.heartbeatService = new PaperHeartbeatService(this, client, nodeId, pool, velocityServerName);
        heartbeatService.start(getConfig().getLong("heartbeat.interval-ticks", 20L));
        if (role == AgentRole.ISLAND_NODE) {
            startIslandNodeWorker(client, nodeId);
        }
        getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    @Override
    public void onDisable() {
        if (jobWorker != null) {
            jobWorker.stop();
        }
        if (heartbeatService != null) {
            heartbeatService.stop();
        }
    }

    public CloudIslandsPaperAgent agent() {
        return agent;
    }

    public ActiveIslandRegistry activeIslands() {
        return activeIslands;
    }

    private void startIslandNodeWorker(CoreApiClient client, String nodeId) {
        IslandStorage storage = PaperStorageFactory.create(this, getConfig());
        ShardWorldManager shardWorldManager = new ShardWorldManager(
            getConfig().getString("island-node.shard-world-prefix", "ci_shard_"),
            getConfig().getInt("island-node.shard-count", 4),
            getConfig().getInt("island-node.cell-size", 1024)
        );
        this.activeIslands = new ActiveIslandRegistry();
        IslandActivationJobHandler activationHandler = new IslandActivationJobHandler(storage, shardWorldManager, agent.protection(), new IslandWorldRestorer(storage, getDataFolder().toPath().resolve("staging")), new ShardWorldPreloader(this), getConfig().getInt("island-node.activation.preload-radius", 4));
        this.jobWorker = new PaperIslandJobWorker(this, new CoreBackedIslandJobSource(client), activationHandler, activeIslands, nodeId);
        jobWorker.start(getConfig().getLong("island-node.activation.worker-interval-ticks", 20L));
    }
}
