package kr.lunaf.cloudislands.coreclient;

public record AdminRouteSessionView(String playerUuid, String ticketId, String targetNode, String targetServerName, String expiresAt) {
    public AdminRouteSessionView {
        playerUuid = playerUuid == null ? "" : playerUuid;
        ticketId = ticketId == null ? "" : ticketId;
        targetNode = targetNode == null ? "" : targetNode;
        targetServerName = targetServerName == null ? "" : targetServerName;
        expiresAt = expiresAt == null ? "" : expiresAt;
    }
}
