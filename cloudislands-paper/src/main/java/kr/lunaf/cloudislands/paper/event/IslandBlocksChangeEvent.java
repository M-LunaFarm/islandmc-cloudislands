package kr.lunaf.cloudislands.paper.event;

import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandBlocksChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID islandId;
    private final String materialKey;
    private final String delta;

    public IslandBlocksChangeEvent(UUID islandId, String materialKey, String delta) {
        super(true);
        this.islandId = islandId;
        this.materialKey = materialKey == null ? "" : materialKey;
        this.delta = delta == null ? "" : delta;
    }

    public UUID islandId() {
        return islandId;
    }

    public String materialKey() {
        return materialKey;
    }

    public String delta() {
        return delta;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
