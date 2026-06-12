package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CoreReloadEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final int clearedSessions;
    private final int clearedTickets;
    private final int clearedRedisKeys;
    private final Map<String, String> fields;

    public CoreReloadEvent(int clearedSessions, int clearedTickets, int clearedRedisKeys, Map<String, String> fields) {
        super(true);
        this.clearedSessions = clearedSessions;
        this.clearedTickets = clearedTickets;
        this.clearedRedisKeys = clearedRedisKeys;
        this.fields = Map.copyOf(fields);
    }

    public int clearedSessions() {
        return clearedSessions;
    }

    public int clearedTickets() {
        return clearedTickets;
    }

    public int clearedRedisKeys() {
        return clearedRedisKeys;
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
