package kr.lunaf.cloudislands.coreservice.upgrade;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandUpgradeRepository implements IslandUpgradeRepository {
    private final IslandUpgradeRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandUpgradeRepository(IslandUpgradeRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public Optional<IslandUpgradeSnapshot> find(UUID islandId, String upgradeKey) {
        Optional<List<IslandUpgradeSnapshot>> cached = cached(islandId);
        if (cached.isPresent()) {
            return cached.get().stream()
                .filter(upgrade -> upgrade.upgradeKey().equals(upgradeKey))
                .findFirst();
        }
        Optional<IslandUpgradeSnapshot> upgrade = delegate.find(islandId, upgradeKey);
        cache(islandId, delegate.list(islandId));
        return upgrade;
    }

    @Override
    public List<IslandUpgradeSnapshot> list(UUID islandId) {
        Optional<List<IslandUpgradeSnapshot>> cached = cached(islandId);
        if (cached.isPresent()) {
            return cached.get();
        }
        return cache(islandId, delegate.list(islandId));
    }

    @Override
    public IslandUpgradeSnapshot setLevel(UUID islandId, String upgradeKey, UpgradeType type, int level) {
        IslandUpgradeSnapshot snapshot = delegate.setLevel(islandId, upgradeKey, type, level);
        cache(islandId, delegate.list(islandId));
        return snapshot;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private List<IslandUpgradeSnapshot> cache(UUID islandId, List<IslandUpgradeSnapshot> upgrades) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandUpgrades(islandId), encode(upgrades));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return upgrades;
    }

    private Optional<List<IslandUpgradeSnapshot>> cached(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.islandUpgrades(islandId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(parse(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static String encode(List<IslandUpgradeSnapshot> upgrades) {
        StringBuilder out = new StringBuilder();
        for (IslandUpgradeSnapshot upgrade : upgrades) {
            out.append(upgrade.islandId()).append('|')
                .append(upgrade.upgradeKey()).append('|')
                .append(upgrade.type().name()).append('|')
                .append(upgrade.level()).append('|')
                .append(upgrade.updatedAt())
                .append('\n');
        }
        return out.toString();
    }

    private static List<IslandUpgradeSnapshot> parse(String value) {
        List<IslandUpgradeSnapshot> upgrades = new ArrayList<>();
        for (String line : value.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\\|", -1);
            if (parts.length != 5) {
                continue;
            }
            try {
                upgrades.add(new IslandUpgradeSnapshot(
                    UUID.fromString(parts[0]),
                    parts[1],
                    UpgradeType.valueOf(parts[2]),
                    Integer.parseInt(parts[3]),
                    instant(parts[4])
                ));
            } catch (RuntimeException ignored) {
                // Skip corrupt Redis cache rows without discarding every cached island upgrade.
            }
        }
        return List.copyOf(upgrades);
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }
}
