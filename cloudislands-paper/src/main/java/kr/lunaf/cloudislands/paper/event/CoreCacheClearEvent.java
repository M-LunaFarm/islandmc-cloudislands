package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CoreCacheClearEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String scope;
    private final int sessions;
    private final int tickets;
    private final int redisKeys;
    private final Map<String, String> fields;

    public CoreCacheClearEvent(String scope, int sessions, int tickets, int redisKeys, Map<String, String> fields) {
        super(true);
        this.scope = scope == null ? "" : scope;
        this.sessions = sessions;
        this.tickets = tickets;
        this.redisKeys = redisKeys;
        this.fields = Map.copyOf(fields);
    }

    public String scope() {
        return scope;
    }

    public int sessions() {
        return sessions;
    }

    public int tickets() {
        return tickets;
    }

    public int redisKeys() {
        return redisKeys;
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
