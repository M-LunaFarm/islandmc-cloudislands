package kr.lunaf.cloudislands.coreservice.repository;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
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
        Optional<IslandSnapshot> island = delegate.findById(islandId);
        island.ifPresent(this::cache);
        return island;
    }

    @Override
    public Optional<IslandSnapshot> findByOwner(UUID ownerUuid) {
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
    public boolean markDeleted(UUID islandId, UUID requesterUuid) {
        boolean deleted = delegate.markDeleted(islandId, requesterUuid);
        if (deleted) {
            deleteCache(islandId);
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
            redis.command("SET", RedisKeys.islandSummary(island.islandId()), islandJson(island));
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
