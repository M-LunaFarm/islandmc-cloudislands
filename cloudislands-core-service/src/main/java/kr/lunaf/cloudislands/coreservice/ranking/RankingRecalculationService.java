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
    private final String levelFormulaExpression;
    private final String worthFormulaType;
    private final long levelPointsDivisor;

    public RankingRecalculationService(RankingRepository rankings, GlobalEventPublisher events) {
        this(rankings, events, "floor(total_level_points / 1000)", "SUM_BLOCK_VALUES");
    }

    public RankingRecalculationService(RankingRepository rankings, GlobalEventPublisher events, String levelFormulaExpression, String worthFormulaType) {
        this.rankings = rankings;
        this.events = events;
        this.levelFormulaExpression = levelFormulaExpression == null || levelFormulaExpression.isBlank() ? "floor(total_level_points / 1000)" : levelFormulaExpression;
        this.worthFormulaType = worthFormulaType == null || worthFormulaType.isBlank() ? "SUM_BLOCK_VALUES" : worthFormulaType;
        this.levelPointsDivisor = parseLevelPointsDivisor(this.levelFormulaExpression);
    }

    public IslandRankSnapshot recalculate(UUID islandId, Map<String, Long> blockCounts, Map<String, BlockValue> values, int memberCount) {
        BigDecimal worth = BigDecimal.ZERO;
        long levelPoints = 0L;
        blockCounts = blockCounts == null ? Map.of() : blockCounts;
        values = values == null ? Map.of() : values;
        for (Map.Entry<String, Long> entry : blockCounts.entrySet()) {
            BlockValue value = values.get(entry.getKey());
            if (value == null || entry.getValue() == null) {
                continue;
            }
            long counted = value.limit() <= 0 ? Math.max(0L, entry.getValue()) : Math.max(0L, Math.min(entry.getValue(), value.limit()));
            worth = worth.add(value.worth().multiply(BigDecimal.valueOf(counted)));
            levelPoints += value.levelPoints() * counted;
        }
        IslandRankSnapshot snapshot = new IslandRankSnapshot(islandId, Math.floorDiv(levelPoints, levelPointsDivisor), worth, Math.max(0, memberCount), Instant.now());
        rankings.save(snapshot);
        events.publish(CloudIslandEventType.ISLAND_LEVEL_UPDATED.name(), Map.of("islandId", islandId.toString(), "level", Long.toString(snapshot.level()), "worth", snapshot.worth().toPlainString(), "levelFormula", levelFormulaExpression));
        events.publish(CloudIslandEventType.ISLAND_WORTH_CHANGED.name(), Map.of("islandId", islandId.toString(), "worth", snapshot.worth().toPlainString(), "worthFormula", worthFormulaType));
        return snapshot;
    }

    public String levelFormulaExpression() {
        return levelFormulaExpression;
    }

    public String worthFormulaType() {
        return worthFormulaType;
    }

    public long levelPointsDivisor() {
        return levelPointsDivisor;
    }

    private static long parseLevelPointsDivisor(String expression) {
        if (expression == null || expression.isBlank()) {
            return 1000L;
        }
        int slash = expression.lastIndexOf('/');
        if (slash < 0) {
            return 1000L;
        }
        StringBuilder digits = new StringBuilder();
        for (int index = slash + 1; index < expression.length(); index++) {
            char current = expression.charAt(index);
            if (Character.isDigit(current)) {
                digits.append(current);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return 1000L;
        }
        try {
            return Math.max(1L, Long.parseLong(digits.toString()));
        } catch (NumberFormatException ignored) {
            return 1000L;
        }
    }

    public record BlockValue(BigDecimal worth, long levelPoints, long limit) {
        public BlockValue {
            worth = worth == null || worth.signum() < 0 ? BigDecimal.ZERO : worth;
            levelPoints = Math.max(0L, levelPoints);
            limit = Math.max(0L, limit);
        }
    }
}
