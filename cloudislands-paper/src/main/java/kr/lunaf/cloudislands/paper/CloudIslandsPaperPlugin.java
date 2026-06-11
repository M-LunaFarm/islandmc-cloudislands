package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import kr.lunaf.cloudislands.paper.admin.AdminCommandController;
import kr.lunaf.cloudislands.paper.session.PaperRouteSessionListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    private CloudIslandsPaperAgent agent;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        String nodeId = getConfig().getString("node.id", "island-1");
        AgentRole role = AgentRole.valueOf(getConfig().getString("node.role", "ISLAND_NODE"));
        CoreApiClient client = new JdkCoreApiClient(URI.create(getConfig().getString("core-api.base-url", "https://core-api.internal:8443")), System.getenv().getOrDefault("CI_CORE_TOKEN", ""), Duration.ofSeconds(3));
        this.agent = new CloudIslandsPaperAgent(this, role, client, nodeId);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(agent.protection()), this);
        getServer().getPluginManager().registerEvents(new PaperRouteSessionListener(client, agent.routeTickets(), nodeId), this);
        PluginCommand admin = getCommand("ciadmin");
        if (admin != null) {
            admin.setExecutor(new AdminCommandController(agent));
        }
        getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    public CloudIslandsPaperAgent agent() {
        return agent;
    }
}
