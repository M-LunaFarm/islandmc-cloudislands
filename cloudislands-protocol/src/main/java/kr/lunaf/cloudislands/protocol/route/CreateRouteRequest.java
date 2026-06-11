package kr.lunaf.cloudislands.protocol.route;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;

public record CreateRouteRequest(UUID playerUuid, UUID islandId, RouteAction action, String warpName) {}
