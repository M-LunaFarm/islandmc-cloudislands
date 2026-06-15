package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RouteTicketFailedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ticketId;
    private final UUID islandId;
    private final UUID playerUuid;
    private final String action;
    private final String targetNode;
    private final String targetServerName;
    private final String requestedNode;
    private final String reason;
    private final Map<String, String> fields;

    public RouteTicketFailedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String requestedNode, String reason, Map<String, String> fields) {
        this(ticketId, islandId, playerUuid, action, targetNode, "", requestedNode, reason, fields);
    }

    public RouteTicketFailedEvent(UUID ticketId, UUID islandId, UUID playerUuid, String action, String targetNode, String targetServerName, String requestedNode, String reason, Map<String, String> fields) {
        super(true);
        this.ticketId = ticketId;
        this.islandId = islandId;
        this.playerUuid = playerUuid;
        this.action = action == null ? "" : action;
        this.targetNode = targetNode == null ? "" : targetNode;
        this.targetServerName = targetServerName == null ? "" : targetServerName;
        this.requestedNode = requestedNode == null ? "" : requestedNode;
        this.reason = reason == null ? "" : reason;
        this.fields = Map.copyOf(fields);
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID islandId() {
        return islandId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String action() {
        return action;
    }

    public String targetNode() {
        return targetNode;
    }

    public String targetServerName() {
        return targetServerName;
    }

    public String requestedNode() {
        return requestedNode;
    }

    public String reason() {
        return reason;
    }

    public Map<String, String> fields() {
        return fields;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
