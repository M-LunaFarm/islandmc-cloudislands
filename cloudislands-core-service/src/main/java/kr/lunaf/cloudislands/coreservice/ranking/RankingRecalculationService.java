package kr.lunaf.cloudislands.coreservice.ranking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;

public final class RankingRecalculationService {
    private final RankingRepository rankings;
    private final GlobalEventPublisher events;

    public RankingRecalculationService(RankingRepository rankings, GlobalEventPublisher events) {
        this.rankings = rankings;
        this.events = events;
    }

    public IslandRankSnapshot recalculate(UUID islandId, Map<String, Long> blockCounts, Map<String, BlockValue> values, int memberCount) {
        BigDecimal worth = BigDecimal.ZERO;
        long levelPoints = 0L;
        for (Map.Entry<String, Long> entry : blockCounts.entrySet()) {
            BlockValue value = values.get(entry.getKey());
            if (value == null) {
                continue;
            }
            long counted = value.limit() <= 0 ? entry.getValue() : Math.min(entry.getValue(), value.limit());
            worth = worth.add(value.worth().multiply(BigDecimal.valueOf(counted)));
            levelPoints += value.levelPoints() * counted;
        }
        IslandRankSnapshot snapshot = new IslandRankSnapshot(islandId, Math.floorDiv(levelPoints, 1000L), worth, memberCount, Instant.now());
        rankings.save(snapshot);
        events.publish(CloudIslandEventType.ISLAND_LEVEL_UPDATED.name(), Map.of("islandId", islandId.toString(), "level", Long.toString(snapshot.level()), "worth", snapshot.worth().toPlainString()));
        return snapshot;
    }

    public record BlockValue(BigDecimal worth, long levelPoints, long limit) {}
}
