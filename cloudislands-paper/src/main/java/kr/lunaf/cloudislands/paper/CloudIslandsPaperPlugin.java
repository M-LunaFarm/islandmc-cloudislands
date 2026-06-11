package kr.lunaf.cloudislands.paper;

import java.net.URI;
import java.time.Duration;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.JdkCoreApiClient;
import org.bukkit.plugin.java.JavaPlugin;

public final class CloudIslandsPaperPlugin extends JavaPlugin {
    private CloudIslandsPaperAgent agent;

    @Override
    public void onEnable() {
        String nodeId = getConfig().getString("node.id", "island-1");
        AgentRole role = AgentRole.valueOf(getConfig().getString("node.role", "ISLAND_NODE"));
        CoreApiClient client = new JdkCoreApiClient(URI.create(getConfig().getString("core-api.base-url", "https://core-api.internal:8443")), System.getenv().getOrDefault("CI_CORE_TOKEN", ""), Duration.ofSeconds(3));
        this.agent = new CloudIslandsPaperAgent(this, role, client, nodeId);
        getServer().getPluginManager().registerEvents(new IslandProtectionListener(agent.protection()), this);
        getLogger().info("CloudIslands Paper agent enabled as " + role + " node " + nodeId);
    }

    public CloudIslandsPaperAgent agent() {
        return agent;
    }
}
