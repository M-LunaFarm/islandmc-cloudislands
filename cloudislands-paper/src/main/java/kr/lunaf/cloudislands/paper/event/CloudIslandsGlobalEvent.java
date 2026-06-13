package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class CloudIslandsGlobalEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final long sequence;
    private final String type;
    private final Map<String, String> fields;
    private final String occurredAt;

    public CloudIslandsGlobalEvent(long sequence, String type, Map<String, String> fields, String occurredAt) {
        super(true);
        this.sequence = sequence;
        this.type = type == null ? "" : type;
        this.fields = Map.copyOf(fields);
        this.occurredAt = occurredAt == null ? "" : occurredAt;
    }

    public long sequence() {
        return sequence;
    }

    public String type() {
        return type;
    }

    public Map<String, String> fields() {
        return fields;
    }

    public String occurredAt() {
        return occurredAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
