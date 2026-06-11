package kr.lunaf.cloudislands.coreservice.ranking;

import java.util.Map;
import java.util.UUID;

public interface IslandLevelRepository {
    void addBlockDelta(UUID islandId, String materialKey, long delta);
    Map<String, Long> blockCounts(UUID islandId);
    Map<String, RankingRecalculationService.BlockValue> blockValues();
    void putBlockValue(String materialKey, RankingRecalculationService.BlockValue value);
}
