package kr.lunaf.cloudislands.coreservice.generator;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandGeneratorRepository implements IslandGeneratorRepository {
    private final IslandGeneratorRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandGeneratorRepository(IslandGeneratorRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public IslandGeneratorSnapshot profile(UUID islandId) {
        Optional<IslandGeneratorSnapshot> cached = cachedProfile(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cacheProfile(delegate.profile(islandId));
    }

    @Override
    public IslandGeneratorSnapshot setProfile(UUID islandId, String generatorKey, int level) {
        return cacheProfile(delegate.setProfile(islandId, generatorKey, level));
    }

    @Override
    public List<GeneratorRuleSnapshot> rules(String generatorKey) {
        String key = safeGeneratorKey(generatorKey);
        Optional<List<GeneratorRuleSnapshot>> cached = cachedRules(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cacheRules(key, delegate.rules(key));
    }

    @Override
    public List<GeneratorRuleSnapshot> setRules(String generatorKey, List<GeneratorRuleSnapshot> rules) {
        String key = safeGeneratorKey(generatorKey);
        return cacheRules(key, delegate.setRules(key, rules));
    }

    public long failuresTotal() {
        return failures.get();
    }

    private IslandGeneratorSnapshot cacheProfile(IslandGeneratorSnapshot profile) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandGeneratorProfile(profile.islandId()), encodeProfile(profile));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return profile;
    }

    private Optional<IslandGeneratorSnapshot> cachedProfile(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandGeneratorProfile(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return parseProfile(value);
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private List<GeneratorRuleSnapshot> cacheRules(String generatorKey, List<GeneratorRuleSnapshot> rules) {
        List<GeneratorRuleSnapshot> snapshots = List.copyOf(rules == null ? List.of() : rules);
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.generatorRules(generatorKey), encodeRules(snapshots));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return snapshots;
    }

    private Optional<List<GeneratorRuleSnapshot>> cachedRules(String generatorKey) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.generatorRules(generatorKey));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parseRules(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encodeProfile(IslandGeneratorSnapshot profile) {
        return profile.islandId() + "|" + profile.generatorKey() + "|" + profile.level() + "|" + profile.updatedAt();
    }

    private static Optional<IslandGeneratorSnapshot> parseProfile(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            return Optional.of(new IslandGeneratorSnapshot(
                UUID.fromString(parts[0]),
                parts[1],
                Integer.parseInt(parts[2]),
                instant(parts[3])
            ));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static String encodeRules(List<GeneratorRuleSnapshot> rules) {
        StringBuilder out = new StringBuilder();
        for (GeneratorRuleSnapshot rule : rules) {
            out.append(rule.generatorKey()).append('|')
                .append(rule.materialKey()).append('|')
                .append(rule.chance()).append('|')
                .append(rule.minIslandLevel()).append('|')
                .append(rule.minUpgradeLevel()).append('|')
                .append(rule.biomeKey()).append('|')
                .append(rule.enabled())
                .append('\n');
        }
        return out.toString();
    }

    private static List<GeneratorRuleSnapshot> parseRules(String value) {
        List<GeneratorRuleSnapshot> rules = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 7) {
                continue;
            }
            try {
                rules.add(new GeneratorRuleSnapshot(
                    parts[0],
                    parts[1],
                    Double.parseDouble(parts[2]),
                    Integer.parseInt(parts[3]),
                    Integer.parseInt(parts[4]),
                    parts[5],
                    Boolean.parseBoolean(parts[6])
                ));
            } catch (RuntimeException ignored) {
                // Skip corrupt Redis cache rows without discarding every cached generator rule.
            }
        }
        return List.copyOf(rules);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String safeGeneratorKey(String generatorKey) {
        return generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey.trim().toLowerCase();
    }
}
