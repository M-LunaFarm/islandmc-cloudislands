package kr.lunaf.cloudislands.api.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IslandLevelRecalculateEvent(UUID islandId, long level, BigDecimal worth, Instant occurredAt) implements CloudIslandEvent {
    public IslandLevelRecalculateEvent(UUID islandId, long level, Instant occurredAt) {
        this(islandId, level, null, occurredAt);
    }
}
