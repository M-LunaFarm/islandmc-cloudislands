package kr.lunaf.cloudislands.coreservice.ranking;

import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DirtyRankingRecalculationTaskTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000401");

    @Test
    void repeatedDirtyMarksAreCoalescedUntilBatchRecalculation() {
        InMemoryRankingRepository rankings = new InMemoryRankingRepository();
        InMemoryIslandLevelRepository levels = new InMemoryIslandLevelRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        DirtyRankingRecalculationTask task = task(rankings, levels, metadata, events);

        levels.replaceBlockCounts(ISLAND, Map.of("minecraft:diamond_block", 250L));
        metadata.upsertMember(ISLAND, UUID.fromString("00000000-0000-0000-0000-000000000411"), IslandRole.OWNER);
        metadata.upsertMember(ISLAND, UUID.fromString("00000000-0000-0000-0000-000000000412"), IslandRole.MEMBER);
        rankings.markDirty(ISLAND);
        rankings.markDirty(ISLAND);
        rankings.markDirty(ISLAND);

        task.runOnce();

        IslandRankSnapshot snapshot = rankings.topByLevel(10).get(0);
        assertEquals(1L, task.lastBatchSize());
        assertEquals(1L, task.drainedTotal());
        assertEquals(1L, task.recalculatedTotal());
        assertEquals(ISLAND, snapshot.islandId());
        assertEquals(2L, snapshot.level());
        assertEquals(new BigDecimal("250000.00"), snapshot.worth());
        assertEquals(2, snapshot.memberCount());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_LEVEL_UPDATED.name()));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_WORTH_CHANGED.name()));

        task.runOnce();

        assertEquals(0L, task.lastBatchSize());
        assertEquals(1L, task.drainedTotal());
        assertEquals(1L, task.recalculatedTotal());
    }

    @Test
    void dirtyQueueProcessesOnlyBatchLimitPerRun() {
        InMemoryRankingRepository rankings = new InMemoryRankingRepository();
        InMemoryIslandLevelRepository levels = new InMemoryIslandLevelRepository();
        DirtyRankingRecalculationTask task = task(rankings, levels, new InMemoryIslandMetadataRepository(), new InMemoryGlobalEventPublisher());

        for (int index = 0; index < 105; index++) {
            UUID islandId = new UUID(0L, 500L + index);
            levels.replaceBlockCounts(islandId, Map.of("minecraft:diamond_block", 1L));
            rankings.markDirty(islandId);
        }

        task.runOnce();

        assertEquals(100L, task.lastBatchSize());
        assertEquals(100L, task.drainedTotal());
        assertEquals(100L, task.recalculatedTotal());
        assertEquals(100, rankings.topByWorth(200).size());

        task.runOnce();

        assertEquals(5L, task.lastBatchSize());
        assertEquals(105L, task.drainedTotal());
        assertEquals(105L, task.recalculatedTotal());
        assertEquals(105, rankings.topByWorth(200).size());
    }

    @Test
    void failedRecalculationReturnsIslandToDirtyQueue() {
        InMemoryRankingRepository rankings = new InMemoryRankingRepository();
        ToggleFailingLevels levels = new ToggleFailingLevels();
        DirtyRankingRecalculationTask task = task(rankings, levels, new InMemoryIslandMetadataRepository(), new InMemoryGlobalEventPublisher());
        rankings.markDirty(ISLAND);

        task.runOnce();

        assertEquals(1L, task.lastBatchSize());
        assertEquals(1L, task.drainedTotal());
        assertEquals(0L, task.recalculatedTotal());
        assertEquals(1L, task.failuresTotal());

        levels.fail = false;
        task.runOnce();

        assertEquals(1L, task.lastBatchSize());
        assertEquals(2L, task.drainedTotal());
        assertEquals(1L, task.recalculatedTotal());
        assertEquals(1L, task.failuresTotal());
        assertEquals(1, rankings.topByWorth(10).size());
    }

    private DirtyRankingRecalculationTask task(
        InMemoryRankingRepository rankings,
        IslandLevelRepository levels,
        InMemoryIslandMetadataRepository metadata,
        InMemoryGlobalEventPublisher events
    ) {
        return new DirtyRankingRecalculationTask(rankings, levels, metadata, new RankingRecalculationService(rankings, events));
    }

    private static final class ToggleFailingLevels implements IslandLevelRepository {
        private boolean fail = true;

        @Override
        public void addBlockDelta(UUID islandId, String materialKey, long delta) {
        }

        @Override
        public void replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        }

        @Override
        public Map<String, Long> blockCounts(UUID islandId) {
            if (fail) {
                throw new IllegalStateException("level store is unavailable");
            }
            return Map.of("minecraft:diamond_block", 1L);
        }

        @Override
        public Map<String, RankingRecalculationService.BlockValue> blockValues() {
            return Map.of(
                "minecraft:diamond_block",
                new RankingRecalculationService.BlockValue(new BigDecimal("1000.00"), 10L, 5000L)
            );
        }

        @Override
        public void putBlockValue(String materialKey, RankingRecalculationService.BlockValue value) {
        }
    }
}
