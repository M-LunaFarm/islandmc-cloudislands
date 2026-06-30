package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class RouteTargetResolver {
    private RouteTargetResolver() {
    }

    public static RouteTargetSelection ready(NodeLoad node, String worldName) {
        return new RouteTargetSelection(node, worldName, RouteTicketState.READY);
    }

    public static RouteTargetSelection preparing(NodeLoad node, String worldName) {
        return new RouteTargetSelection(node, worldName, RouteTicketState.PREPARING);
    }
}
