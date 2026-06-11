package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class CloudIslandsPaperAgent {
    private final AgentRole role;
    private final RouteTicketConsumer routeTicketConsumer;
    private final ProtectionController protectionController;

    public CloudIslandsPaperAgent(AgentRole role, CoreApiClient coreApiClient, String nodeId) {
        this.role = role;
        this.routeTicketConsumer = new RouteTicketConsumer(coreApiClient, nodeId);
        this.protectionController = new ProtectionController(new RegionIndex());
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
