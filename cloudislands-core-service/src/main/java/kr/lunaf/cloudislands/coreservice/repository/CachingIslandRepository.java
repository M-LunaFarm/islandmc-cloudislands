package kr.lunaf.cloudislands.coreservice.repository;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.cache.RedisTtls;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandRepository implements IslandRepository {
    private final IslandRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandRepository(IslandRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public Optional<IslandSnapshot> findById(UUID islandId) {
        Optional<IslandSnapshot> cached = cachedIsland(islandId);
        if (cached.isPresent()) {
            return cached;
        }
        Optional<IslandSnapshot> island = delegate.findById(islandId);
        island.ifPresent(this::cache);
        return island;
    }

    @Override
    public Optional<IslandSnapshot> findByOwner(UUID ownerUuid) {
        Optional<UUID> cachedIslandId = cachedOwnerIslandId(ownerUuid);
        if (cachedIslandId.isPresent()) {
            Optional<IslandSnapshot> cached = findById(cachedIslandId.get());
            if (cached.isPresent()) {
                return cached;
            }
        }
        Optional<IslandSnapshot> island = delegate.findByOwner(ownerUuid);
        island.ifPresent(this::cache);
        return island;
    }

    @Override
    public Optional<IslandSnapshot> findByName(String name) {
        Optional<IslandSnapshot> island = delegate.findByName(name);
        island.ifPresent(this::cache);
        return island;
    }

    @Override
    public Optional<String> templateId(UUID islandId) {
        return delegate.templateId(islandId);
    }

    @Override
    public IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name) {
        return cache(delegate.createOwnedIsland(islandId, ownerUuid, templateId, name));
    }

    @Override
    public void setState(UUID islandId, IslandState state) {
        delegate.setState(islandId, state);
        Optional<IslandSnapshot> island = delegate.findById(islandId);
        if (island.isPresent()) {
            cache(island.get());
        } else if (state == IslandState.DELETED) {
            deleteCache(islandId);
        }
    }

    @Override
    public void updateStats(UUID islandId, int size, long level, String worth) {
        delegate.updateStats(islandId, size, level, worth);
        delegate.findById(islandId).ifPresent(this::cache);
    }

    @Override
    public boolean rename(UUID islandId, String name) {
        boolean renamed = delegate.rename(islandId, name);
        if (renamed) {
            delegate.findById(islandId).ifPresent(this::cache);
        }
        return renamed;
    }

    @Override
    public boolean markDeleted(UUID islandId, UUID requesterUuid) {
        Optional<IslandSnapshot> current = delegate.findById(islandId);
        boolean deleted = delegate.markDeleted(islandId, requesterUuid);
        if (deleted) {
            deleteCache(islandId);
            current.map(IslandSnapshot::ownerUuid).ifPresent(this::deleteOwnerCache);
        }
        return deleted;
    }

    @Override
    public Optional<IslandSnapshot> restoreDeleted(UUID islandId) {
        Optional<IslandSnapshot> island = delegate.restoreDeleted(islandId);
        island.ifPresent(this::cache);
        return island;
    }

    @Override
    public boolean transferOwnership(UUID islandId, UUID currentOwnerUuid, UUID newOwnerUuid) {
        boolean transferred = delegate.transferOwnership(islandId, currentOwnerUuid, newOwnerUuid);
        if (transferred) {
            deleteOwnerCache(currentOwnerUuid);
            delegate.findById(islandId).ifPresent(this::cache);
        }
        return transferred;
    }

    @Override
    public void createOwnerMember(UUID islandId, UUID ownerUuid) {
        delegate.createOwnerMember(islandId, ownerUuid);
    }

    @Override
    public void createRuntime(UUID islandId, String state) {
        delegate.createRuntime(islandId, state);
    }

    public long failuresTotal() {
        return failures.get();
    }

    private IslandSnapshot cache(IslandSnapshot island) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandSummary(island.islandId()), islandJson(island), "PX", Long.toString(RedisTtls.ISLAND_SUMMARY_MILLIS));
            redis.command("SET", RedisKeys.playerIsland(island.ownerUuid()), island.islandId().toString(), "PX", Long.toString(RedisTtls.PLAYER_ISLAND_MILLIS));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return island;
    }

    private void deleteCache(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("DEL", RedisKeys.islandSummary(islandId));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private void deleteOwnerCache(UUID ownerUuid) {
        if (ownerUuid == null) {
            return;
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("DEL", RedisKeys.playerIsland(ownerUuid));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    private Optional<IslandSnapshot> cachedIsland(UUID islandId) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String json = redis.command("GET", RedisKeys.islandSummary(islandId));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            IslandSnapshot island = new IslandSnapshot(
                JsonFields.uuid(json, "islandId", islandId),
                JsonFields.uuid(json, "ownerUuid", new UUID(0L, 0L)),
                JsonFields.text(json, "name", ""),
                JsonFields.enumValue(IslandState.class, json, "state", IslandState.INACTIVE_READY),
                JsonFields.integer(json, "size", 300),
                JsonFields.longValue(json, "level", 0L),
                JsonFields.text(json, "worth", "0.00"),
                JsonFields.bool(json, "publicAccess", false),
                instant(JsonFields.text(json, "createdAt", "")),
                instant(JsonFields.text(json, "updatedAt", ""))
            );
            return island.state() == IslandState.DELETED ? Optional.empty() : Optional.of(island);
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private Optional<UUID> cachedOwnerIslandId(UUID ownerUuid) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String value = redis.command("GET", RedisKeys.playerIsland(ownerUuid));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(value));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
            return Optional.empty();
        }
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static String islandJson(IslandSnapshot island) {
        return new StringBuilder("{")
            .append("\"islandId\":\"").append(island.islandId()).append("\",")
            .append("\"ownerUuid\":\"").append(island.ownerUuid()).append("\",")
            .append("\"name\":\"").append(escape(island.name())).append("\",")
            .append("\"state\":\"").append(island.state().name()).append("\",")
            .append("\"size\":").append(island.size()).append(',')
            .append("\"level\":").append(island.level()).append(',')
            .append("\"worth\":\"").append(escape(island.worth())).append("\",")
            .append("\"publicAccess\":").append(island.publicAccess()).append(',')
            .append("\"createdAt\":\"").append(island.createdAt()).append("\",")
            .append("\"updatedAt\":\"").append(island.updatedAt()).append("\"")
            .append('}')
            .toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
