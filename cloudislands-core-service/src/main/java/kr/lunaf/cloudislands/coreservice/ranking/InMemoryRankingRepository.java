package kr.lunaf.cloudislands.coreservice.ranking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryRankingRepository implements RankingRepository {
    private final Map<UUID, IslandRankSnapshot> snapshots = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();

    @Override
    public void markDirty(UUID islandId) {
        dirty.add(islandId);
    }

    @Override
    public List<UUID> drainDirty(int limit) {
        List<UUID> drained = dirty.stream().limit(Math.max(1, limit)).toList();
        dirty.removeAll(drained);
        return drained;
    }

    @Override
    public long dirtyCount() {
        return dirty.size();
    }

    @Override
    public void setIgnored(UUID islandId, boolean ignored) {
        if (islandId == null) {
            return;
        }
        if (ignored) {
            this.ignored.add(islandId);
        } else {
            this.ignored.remove(islandId);
        }
    }

    @Override
    public boolean isIgnored(UUID islandId) {
        return islandId != null && ignored.contains(islandId);
    }

    @Override
    public void save(IslandRankSnapshot snapshot) {
        snapshots.put(snapshot.islandId(), snapshot);
        dirty.remove(snapshot.islandId());
    }

    @Override
    public List<IslandRankSnapshot> topByLevel(int limit) {
        return snapshots.values().stream()
            .filter(snapshot -> !isIgnored(snapshot.islandId()))
            .sorted(Comparator.comparingLong(IslandRankSnapshot::level).thenComparing(IslandRankSnapshot::worth).reversed())
            .limit(limit)
            .toList();
    }

    @Override
    public List<IslandRankSnapshot> topByWorth(int limit) {
        return snapshots.values().stream()
            .filter(snapshot -> !isIgnored(snapshot.islandId()))
            .sorted(Comparator.comparing(IslandRankSnapshot::worth).thenComparingLong(IslandRankSnapshot::level).reversed())
            .limit(limit)
            .toList();
    }

    public Set<UUID> drainDirty() {
        Set<UUID> copy = new HashSet<>(dirty);
        dirty.removeAll(copy);
        return copy;
    }

    public void seed(UUID islandId) {
        save(new IslandRankSnapshot(islandId, 0L, BigDecimal.ZERO, 1, Instant.now()));
    }
}
