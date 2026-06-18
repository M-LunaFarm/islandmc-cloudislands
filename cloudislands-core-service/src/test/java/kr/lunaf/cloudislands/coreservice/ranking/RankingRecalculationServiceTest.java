package kr.lunaf.cloudislands.coreservice.ranking;

import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RankingRecalculationServiceTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000601");

    
    @Test
    void exposesGoalRankingUpdateContract() {
        assertEquals("block-delta-dirty-flag-batch-recalculate-ranking-snapshot", RankingRecalculationService.UPDATE_POLICY);
        assertEquals("config-loaded-values-plus-admin-api-overrides-worth-level-limit", RankingRecalculationService.BLOCK_VALUE_POLICY);
        assertEquals("periodic-full-scan-replaces-block-counts-and-marks-island-dirty", RankingRecalculationService.FULL_SCAN_POLICY);
        assertEquals("ranking-snapshot-query-backed-by-postgresql-and-redis-ranking-cache", RankingRecalculationService.CACHE_POLICY);
        assertEquals(100, DirtyRankingRecalculationTask.BATCH_LIMIT);
        assertEquals(30L, DirtyRankingRecalculationTask.PERIOD_SECONDS);
    }

    @Test
    void appliesBlockValueLimitsAndIgnoresUndefinedOrNegativeCounts() {
        InMemoryRankingRepository rankings = new InMemoryRankingRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        RankingRecalculationService service = new RankingRecalculationService(
            rankings,
            events,
            "floor(total_level_points / 100)",
            "SUM_BLOCK_VALUES"
        );

        IslandRankSnapshot snapshot = service.recalculate(
            ISLAND,
            Map.of(
                "minecraft:diamond_block", 3L,
                "minecraft:emerald_block", -5L,
                "minecraft:unknown_block", 99L
            ),
            Map.of(
                "minecraft:diamond_block", new RankingRecalculationService.BlockValue(new BigDecimal("1000.00"), 10L, 2L),
                "minecraft:emerald_block", new RankingRecalculationService.BlockValue(new BigDecimal("800.00"), 8L, 10L)
            ),
            4
        );

        assertEquals(ISLAND, snapshot.islandId());
        assertEquals(0L, snapshot.level());
        assertEquals(new BigDecimal("2000.00"), snapshot.worth());
        assertEquals(4, snapshot.memberCount());
        assertEquals(snapshot, rankings.topByWorth(1).getFirst());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_LEVEL_UPDATED.name()));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_WORTH_CHANGED.name()));
        assertTrue(events.toJson().contains("floor(total_level_points / 100)"));
        assertTrue(events.toJson().contains("SUM_BLOCK_VALUES"));
    }

    @Test
    void sanitizesNegativeBlockValueConfig() {
        RankingRecalculationService.BlockValue value = new RankingRecalculationService.BlockValue(
            new BigDecimal("-1.00"),
            -10L,
            -50L
        );

        assertEquals(BigDecimal.ZERO, value.worth());
        assertEquals(0L, value.levelPoints());
        assertEquals(0L, value.limit());
    }
}
