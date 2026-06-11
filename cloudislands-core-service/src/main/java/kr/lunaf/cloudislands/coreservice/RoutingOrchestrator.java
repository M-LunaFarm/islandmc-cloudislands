package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class RoutingOrchestrator {
    private final InMemoryNodeRegistry nodes;
    private final NodeAllocator allocator;

    public RoutingOrchestrator(InMemoryNodeRegistry nodes, NodeAllocator allocator) {
        this.nodes = nodes;
        this.allocator = allocator;
    }

    public String prepareHomeRouteJson() {
        return ticketJson(RouteAction.HOME);
    }

    public String prepareVisitRouteJson() {
        return ticketJson(RouteAction.VISIT);
    }

    private String ticketJson(RouteAction action) {
        NodeLoad selected = allocator.selectBestNode(nodes.snapshot(), Instant.now())
            .orElseThrow(() -> new IllegalStateException("no eligible island node"));
        return "{\"ticketId\":\"" + UUID.randomUUID() + "\",\"state\":\"READY\",\"action\":\"" + action + "\",\"targetNode\":\"" + selected.nodeId() + "\",\"targetServerName\":\"" + selected.velocityServerName() + "\"}";
    }
}
