package kr.lunaf.cloudislands.coreservice.ranking;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIslandLevelRepository implements IslandLevelRepository {
    private final Map<UUID, Map<String, Long>> counts = new ConcurrentHashMap<>();
    private final Map<String, RankingRecalculationService.BlockValue> values = new ConcurrentHashMap<>();

    public InMemoryIslandLevelRepository() {
        values.put("minecraft:diamond_block", new RankingRecalculationService.BlockValue(new BigDecimal("1000.00"), 10L, 5000L));
        values.put("minecraft:emerald_block", new RankingRecalculationService.BlockValue(new BigDecimal("800.00"), 8L, 5000L));
        values.put("minecraft:spawner", new RankingRecalculationService.BlockValue(new BigDecimal("5000.00"), 50L, 200L));
    }

    @Override
    public void addBlockDelta(UUID islandId, String materialKey, long delta) {
        counts.computeIfAbsent(islandId, ignored -> new ConcurrentHashMap<>()).merge(materialKey, delta, (current, change) -> Math.max(0L, current + change));
    }

    @Override
    public void replaceBlockCounts(UUID islandId, Map<String, Long> replacement) {
        Map<String, Long> sanitized = new ConcurrentHashMap<>();
        replacement.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value > 0L) {
                sanitized.put(key, value);
            }
        });
        counts.put(islandId, sanitized);
    }

    @Override
    public Map<String, Long> blockCounts(UUID islandId) {
        return Map.copyOf(counts.getOrDefault(islandId, Map.of()));
    }

    @Override
    public Map<String, RankingRecalculationService.BlockValue> blockValues() {
        return Map.copyOf(values);
    }

    @Override
    public void putBlockValue(String materialKey, RankingRecalculationService.BlockValue value) {
        values.put(materialKey, value);
    }
}
