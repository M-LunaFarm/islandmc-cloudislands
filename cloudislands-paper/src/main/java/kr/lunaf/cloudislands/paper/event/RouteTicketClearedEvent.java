package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class RouteTicketClearedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ticketId;
    private final UUID playerUuid;
    private final String targetNode;
    private final String reason;
    private final boolean clearedSession;
    private final boolean clearedTicket;
    private final Map<String, String> fields;

    public RouteTicketClearedEvent(UUID ticketId, UUID playerUuid, String reason, boolean clearedSession, boolean clearedTicket, Map<String, String> fields) {
        super(true);
        this.ticketId = ticketId;
        this.playerUuid = playerUuid;
        this.targetNode = fields == null ? "" : fields.getOrDefault("targetNode", "");
        this.reason = reason == null ? "" : reason;
        this.clearedSession = clearedSession;
        this.clearedTicket = clearedTicket;
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public UUID ticketId() {
        return ticketId;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public String targetNode() {
        return targetNode;
    }

    public String reason() {
        return reason;
    }

    public boolean clearedSession() {
        return clearedSession;
    }

    public boolean clearedTicket() {
        return clearedTicket;
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
