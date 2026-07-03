package kr.lunaf.cloudislands.coreservice.ranking;

import java.util.List;
import java.util.UUID;

public interface RankingRepository {
    void markDirty(UUID islandId);
    List<UUID> drainDirty(int limit);
    long dirtyCount();
    default void setIgnored(UUID islandId, boolean ignored) {
    }
    default boolean isIgnored(UUID islandId) {
        return false;
    }
    void save(IslandRankSnapshot snapshot);
    List<IslandRankSnapshot> topByLevel(int limit);
    List<IslandRankSnapshot> topByWorth(int limit);
}
