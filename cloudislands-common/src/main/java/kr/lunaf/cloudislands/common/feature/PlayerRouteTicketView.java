package kr.lunaf.cloudislands.common.feature;

import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;

import java.util.Map;
import java.util.UUID;

public record PlayerRouteTicketView(
        UUID islandId,
        RouteAction action,
        RouteTicketState state,
        String destination,
        Map<String, String> payload
) {
    public PlayerRouteTicketView {
        payload = PlayerFacingIslandSurface.sanitize(payload == null ? Map.of() : payload);
        destination = logicalDestination(destination, action);
    }

    public static PlayerRouteTicketView from(RouteTicket ticket) {
        return new PlayerRouteTicketView(
                ticket.islandId(),
                ticket.action(),
                ticket.state(),
                destinationFor(ticket),
                ticket.payload()
        );
    }

    public boolean exposesRouteSecret() {
        return payload.keySet().stream().anyMatch(PlayerFacingIslandSurface::isHiddenTopologyKey);
    }

    private static String destinationFor(RouteTicket ticket) {
        String surface = ticket.payload().getOrDefault("surface", "");
        if (PlayerFacingIslandSurface.isLogicalSurface(surface)) {
            return surface;
        }
        return logicalDestination("", ticket.action());
    }

    private static String logicalDestination(String destination, RouteAction action) {
        if (PlayerFacingIslandSurface.isLogicalSurface(destination)) {
            return destination;
        }
        if (action == null) {
            return "my-island";
        }
        return switch (action) {
            case HOME, RETURN_AFTER_MIGRATION -> "my-island";
            case VISIT -> "island-visit";
            case WARP -> "island-warps";
            case ADMIN_TELEPORT -> "other-island";
        };
    }
}
