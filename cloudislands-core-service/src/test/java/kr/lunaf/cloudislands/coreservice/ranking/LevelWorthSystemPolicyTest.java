package kr.lunaf.cloudislands.coreservice.ranking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelWorthSystemPolicyTest {
    @Test
    void pinsDeltaFullScanSnapshotAndCachePipeline() {
        assertEquals("event-delta>dirty-flag>batch-recalc>ranking-snapshot>redis-ranking-cache", LevelWorthSystemPolicy.UPDATE_PIPELINE);
        assertEquals("periodic-island-chunk-scan>replace-block-counts>correct-drift>mark-dirty", LevelWorthSystemPolicy.FULL_SCAN_PIPELINE);
        assertEquals("block-delta-dirty-flag-batch-recalculate-ranking-snapshot", RankingRecalculationService.UPDATE_POLICY);
        assertEquals("periodic-full-scan-replaces-block-counts-and-marks-island-dirty", RankingRecalculationService.FULL_SCAN_POLICY);
        assertEquals("ranking-snapshot-query-backed-by-postgresql-and-redis-ranking-cache", RankingRecalculationService.CACHE_POLICY);
        assertEquals("island_rank_snapshots(island_id,level,worth,member_count,updated_at)", LevelWorthSystemPolicy.SNAPSHOT_TABLE);
    }

    @Test
    void pinsEventDeltaSourcesFromGoal() {
        assertTrue(LevelWorthSystemPolicy.deltaEvents().contains("BlockPlaceEvent:block_counts+1"));
        assertTrue(LevelWorthSystemPolicy.deltaEvents().contains("BlockBreakEvent:block_counts-1"));
        assertTrue(LevelWorthSystemPolicy.deltaEvents().contains("EntityPlaceEvent:entity_counts+1"));
        assertTrue(LevelWorthSystemPolicy.deltaEvents().contains("EntityRemove:entity_counts-1"));
        assertTrue(LevelWorthSystemPolicy.deltaEvent("BlockPlaceEvent"));
        assertTrue(LevelWorthSystemPolicy.deltaEvent("EntityRemove"));
    }

    @Test
    void pinsFormulaDefaults() {
        RankingRecalculationService service = new RankingRecalculationService(new InMemoryRankingRepository(), new kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher());

        assertEquals("EXPRESSION", LevelWorthSystemPolicy.LEVEL_FORMULA_TYPE);
        assertEquals("floor(total_level_points / 1000)", LevelWorthSystemPolicy.LEVEL_FORMULA_EXPRESSION);
        assertEquals("SUM_BLOCK_VALUES", LevelWorthSystemPolicy.WORTH_FORMULA_TYPE);
        assertEquals(LevelWorthSystemPolicy.LEVEL_FORMULA_EXPRESSION, service.levelFormulaExpression());
        assertEquals(LevelWorthSystemPolicy.WORTH_FORMULA_TYPE, service.worthFormulaType());
        assertEquals(1000L, service.levelPointsDivisor());
    }

    @Test
    void bundledBlockValuesContainGoalExamples() {
        Map<String, RankingRecalculationService.BlockValue> values = ConfigBlockValues.load("");

        assertBlockValue(values.get("minecraft:diamond_block"), "1000", 10L, 5000L);
        assertBlockValue(values.get("minecraft:emerald_block"), "800", 8L, 5000L);
        assertBlockValue(values.get("minecraft:spawner"), "5000", 50L, 200L);
    }

    private void assertBlockValue(RankingRecalculationService.BlockValue value, String worth, long levelPoints, long limit) {
        assertEquals(new BigDecimal(worth), value.worth());
        assertEquals(levelPoints, value.levelPoints());
        assertEquals(limit, value.limit());
    }
}
