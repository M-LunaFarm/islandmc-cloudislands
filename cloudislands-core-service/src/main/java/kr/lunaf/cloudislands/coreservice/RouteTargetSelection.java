package kr.lunaf.cloudislands.coreservice;

import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public record RouteTargetSelection(NodeLoad node, String worldName, RouteTicketState state) {
}
