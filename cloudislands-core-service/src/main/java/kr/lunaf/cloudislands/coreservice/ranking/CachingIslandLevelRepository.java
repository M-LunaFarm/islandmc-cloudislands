package kr.lunaf.cloudislands.coreservice.ranking;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandLevelRepository implements IslandLevelRepository {
    private final IslandLevelRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandLevelRepository(IslandLevelRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public void addBlockDelta(UUID islandId, String materialKey, long delta) {
        delegate.addBlockDelta(islandId, materialKey, delta);
        cacheBlockCounts(islandId, delegate.blockCounts(islandId));
    }

    @Override
    public void replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        delegate.replaceBlockCounts(islandId, counts);
        cacheBlockCounts(islandId, delegate.blockCounts(islandId));
    }

    @Override
    public Map<String, Long> blockCounts(UUID islandId) {
        Optional<Map<String, Long>> cached = cachedBlockCounts(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cacheBlockCounts(islandId, delegate.blockCounts(islandId));
    }

    @Override
    public Map<String, RankingRecalculationService.BlockValue> blockValues() {
        Optional<Map<String, RankingRecalculationService.BlockValue>> cached = cachedBlockValues();
        if (cached.isPresent()) {
            return cached.get();
        }
        return cacheBlockValues(delegate.blockValues());
    }

    @Override
    public void putBlockValue(String materialKey, RankingRecalculationService.BlockValue value) {
        delegate.putBlockValue(materialKey, value);
        cacheBlockValues(delegate.blockValues());
    }

    public long failuresTotal() {
        return failures.get();
    }

    private Map<String, Long> cacheBlockCounts(UUID islandId, Map<String, Long> counts) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandBlockCounts(islandId), encodeCounts(counts));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return counts;
    }

    private Optional<Map<String, Long>> cachedBlockCounts(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandBlockCounts(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parseCounts(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private Map<String, RankingRecalculationService.BlockValue> cacheBlockValues(Map<String, RankingRecalculationService.BlockValue> values) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.blockValues(), encodeValues(values));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return values;
    }

    private Optional<Map<String, RankingRecalculationService.BlockValue>> cachedBlockValues() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.blockValues());
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parseValues(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encodeCounts(Map<String, Long> counts) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            out.append(entry.getKey()).append('|').append(entry.getValue()).append('\n');
        }
        return out.toString();
    }

    private static Map<String, Long> parseCounts(String value) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length == 2) {
                try {
                    counts.put(parts[0], Long.parseLong(parts[1]));
                } catch (RuntimeException ignored) {
                }
            }
        }
        return Map.copyOf(counts);
    }

    private static String encodeValues(Map<String, RankingRecalculationService.BlockValue> values) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, RankingRecalculationService.BlockValue> entry : values.entrySet()) {
            RankingRecalculationService.BlockValue value = entry.getValue();
            out.append(entry.getKey()).append('|')
                .append(value.worth()).append('|')
                .append(value.levelPoints()).append('|')
                .append(value.limit())
                .append('\n');
        }
        return out.toString();
    }

    private static Map<String, RankingRecalculationService.BlockValue> parseValues(String value) {
        Map<String, RankingRecalculationService.BlockValue> values = new LinkedHashMap<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length == 4) {
                try {
                    values.put(parts[0], new RankingRecalculationService.BlockValue(new BigDecimal(parts[1]), Long.parseLong(parts[2]), Long.parseLong(parts[3])));
                } catch (RuntimeException ignored) {
                }
            }
        }
        return Map.copyOf(values);
    }
}
