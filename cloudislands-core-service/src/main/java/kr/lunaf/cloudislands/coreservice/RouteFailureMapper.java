package kr.lunaf.cloudislands.coreservice;

public final class RouteFailureMapper {
    private RouteFailureMapper() {
    }

    public static RouteFailureResponse map(RouteFailureCode code, String detail, String activeNode) {
        RouteFailureCode normalized = code == null ? RouteFailureCode.NO_READY_NODE : code;
        return switch (normalized) {
            case VISITOR_SOFT_FULL -> new RouteFailureResponse(429, "VISITOR_SOFT_FULL", "The island node is reserving slots for members", "", "VISITOR_SOFT_FULL", false);
            case ACTIVATION_LOCKED -> new RouteFailureResponse(409, "ACTIVATION_LOCKED", "Island activation is already in progress", "", "ACTIVATION_LOCKED", false);
            case ACTIVE_NODE_UNAVAILABLE -> new RouteFailureResponse(409, "NODE_UNAVAILABLE", "No eligible island node is available", activeNode, detail, true);
            case NO_READY_NODE -> new RouteFailureResponse(409, "NODE_UNAVAILABLE", "No ready island node is available", "", detail, true);
            default -> new RouteFailureResponse(409, "NODE_UNAVAILABLE", "No eligible island node is available", "", normalized.name(), false);
        };
    }

    public record RouteFailureResponse(int status, String publicReason, String message, String targetNode, String debugReason, boolean includeRoutingDetails) {
    }
}
