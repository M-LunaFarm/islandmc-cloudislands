package kr.lunaf.cloudislands.coreservice.ranking;

import java.util.List;
import java.util.UUID;

public interface RankingRepository {
    void markDirty(UUID islandId);
    List<UUID> drainDirty(int limit);
    void save(IslandRankSnapshot snapshot);
    List<IslandRankSnapshot> topByLevel(int limit);
    List<IslandRankSnapshot> topByWorth(int limit);
}
