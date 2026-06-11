package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.plugin.Plugin;

public final class CloudIslandsPaperAgent {
    private final Plugin plugin;
    private final AgentRole role;
    private final RouteTicketConsumer routeTicketConsumer;
    private final ProtectionController protectionController;

    public CloudIslandsPaperAgent(Plugin plugin, AgentRole role, CoreApiClient coreApiClient, String nodeId) {
        this.plugin = plugin;
        this.role = role;
        this.routeTicketConsumer = new RouteTicketConsumer(plugin, coreApiClient, nodeId);
        this.protectionController = new ProtectionController(new RegionIndex());
    }

    public Plugin plugin() {
        return plugin;
    }

    public AgentRole role() {
        return role;
    }

    public RouteTicketConsumer routeTickets() {
        return routeTicketConsumer;
    }

    public ProtectionController protection() {
        return protectionController;
    }
}
