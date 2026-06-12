package kr.lunaf.cloudislands.paper.event;

import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandTemplateChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String templateId;
    private final Boolean enabled;
    private final String operation;
    private final String minNodeVersion;
    private final Map<String, String> fields;

    public IslandTemplateChangeEvent(String templateId, Boolean enabled, String operation, String minNodeVersion, Map<String, String> fields) {
        super(true);
        this.templateId = templateId == null ? "" : templateId;
        this.enabled = enabled;
        this.operation = operation == null ? "" : operation;
        this.minNodeVersion = minNodeVersion == null ? "" : minNodeVersion;
        this.fields = Map.copyOf(fields);
    }

    public String templateId() {
        return templateId;
    }

    public Boolean enabled() {
        return enabled;
    }

    public String operation() {
        return operation;
    }

    public String minNodeVersion() {
        return minNodeVersion;
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
