package kr.lunaf.cloudislands.paper.event;

import java.math.BigDecimal;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class IslandBlockValueChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String materialKey;
    private final BigDecimal worth;
    private final long levelPoints;
    private final long limit;

    public IslandBlockValueChangeEvent(String materialKey, BigDecimal worth, long levelPoints, long limit) {
        super(true);
        this.materialKey = materialKey == null ? "" : materialKey;
        this.worth = worth == null ? BigDecimal.ZERO : worth;
        this.levelPoints = levelPoints;
        this.limit = limit;
    }

    public String materialKey() {
        return materialKey;
    }

    public BigDecimal worth() {
        return worth;
    }

    public long levelPoints() {
        return levelPoints;
    }

    public long limit() {
        return limit;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
