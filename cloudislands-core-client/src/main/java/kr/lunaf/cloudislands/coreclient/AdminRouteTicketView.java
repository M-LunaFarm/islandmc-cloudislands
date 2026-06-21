package kr.lunaf.cloudislands.coreclient;

public record AdminRouteTicketView(String ticketId, String playerUuid, String islandId, String action, String state, String targetNode, String targetServerName, String targetType, String homeName, String warpName, String expiresAt) {
    public AdminRouteTicketView {
        ticketId = ticketId == null ? "" : ticketId;
        playerUuid = playerUuid == null ? "" : playerUuid;
        islandId = islandId == null ? "" : islandId;
        action = action == null ? "" : action;
        state = state == null ? "" : state;
        targetNode = targetNode == null ? "" : targetNode;
        targetServerName = targetServerName == null ? "" : targetServerName;
        targetType = targetType == null ? "" : targetType;
        homeName = homeName == null ? "" : homeName;
        warpName = warpName == null ? "" : warpName;
        expiresAt = expiresAt == null ? "" : expiresAt;
    }
}
