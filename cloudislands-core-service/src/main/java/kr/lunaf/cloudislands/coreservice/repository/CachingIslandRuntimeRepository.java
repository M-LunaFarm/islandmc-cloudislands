package kr.lunaf.cloudislands.coreservice.repository;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class CachingIslandRuntimeRepository implements IslandRuntimeRepository {
    private final IslandRuntimeRepository delegate;
    private final URI redisUri;
    private final AtomicLong failures = new AtomicLong();

    public CachingIslandRuntimeRepository(IslandRuntimeRepository delegate, URI redisUri) {
        this.delegate = delegate;
        this.redisUri = redisUri;
    }

    @Override
    public Optional<IslandRuntimeSnapshot> find(UUID islandId) {
        return delegate.find(islandId);
    }

    @Override
    public List<IslandRuntimeSnapshot> listByNode(String nodeId, int limit) {
        return delegate.listByNode(nodeId, limit);
    }

    @Override
    public IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, String targetWorld, int cellX, int cellZ) {
        return cache(delegate.markActivating(islandId, targetNode, targetWorld, cellX, cellZ));
    }

    @Override
    public IslandRuntimeSnapshot markActive(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long fencingToken) {
        return cache(delegate.markActive(islandId, nodeId, worldName, cellX, cellZ, fencingToken));
    }

    @Override
    public IslandRuntimeSnapshot markSaving(UUID islandId) {
        return cache(delegate.markSaving(islandId));
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId) {
        return cache(delegate.markInactive(islandId));
    }

    @Override
    public IslandRuntimeSnapshot markInactive(UUID islandId, long fencingToken) {
        return cache(delegate.markInactive(islandId, fencingToken));
    }

    @Override
    public IslandRuntimeSnapshot markMigrating(UUID islandId, String targetNode) {
        return cache(delegate.markMigrating(islandId, targetNode));
    }

    @Override
    public IslandRuntimeSnapshot markQuarantined(UUID islandId, String reason) {
        return cache(delegate.markQuarantined(islandId, reason));
    }

    @Override
    public IslandRuntimeSnapshot setState(UUID islandId, IslandState state) {
        return cache(delegate.setState(islandId, state));
    }

    @Override
    public Map<String, Long> countsByState() {
        return delegate.countsByState();
    }

    @Override
    public int markRecoveryRequiredForNode(String nodeId) {
        List<IslandRuntimeSnapshot> affected = delegate.listByNode(nodeId, 200);
        int changed = delegate.markRecoveryRequiredForNode(nodeId);
        if (changed > 0) {
            for (IslandRuntimeSnapshot runtime : affected) {
                cache(new IslandRuntimeSnapshot(
                    runtime.islandId(),
                    IslandState.RECOVERY_REQUIRED,
                    runtime.activeNode(),
                    runtime.activeWorld(),
                    runtime.cellX(),
                    runtime.cellZ(),
                    runtime.leaseOwner(),
                    runtime.fencingToken(),
                    runtime.activatedAt(),
                    runtime.lastHeartbeat()
                ));
            }
        }
        return changed;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private IslandRuntimeSnapshot cache(IslandRuntimeSnapshot runtime) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.islandRuntime(runtime.islandId()), runtimeJson(runtime));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
        return runtime;
    }

    private static String runtimeJson(IslandRuntimeSnapshot runtime) {
        return new StringBuilder("{")
            .append("\"islandId\":\"").append(runtime.islandId()).append("\",")
            .append("\"state\":\"").append(runtime.state().name()).append("\",")
            .append("\"activeNode\":\"").append(escape(runtime.activeNode())).append("\",")
            .append("\"activeWorld\":\"").append(escape(runtime.activeWorld())).append("\",")
            .append("\"cellX\":").append(nullable(runtime.cellX())).append(',')
            .append("\"cellZ\":").append(nullable(runtime.cellZ())).append(',')
            .append("\"leaseOwner\":\"").append(escape(runtime.leaseOwner())).append("\",")
            .append("\"fencingToken\":").append(runtime.fencingToken()).append(',')
            .append("\"activatedAt\":\"").append(runtime.activatedAt() == null ? "" : runtime.activatedAt()).append("\",")
            .append("\"lastHeartbeat\":\"").append(runtime.lastHeartbeat() == null ? "" : runtime.lastHeartbeat()).append("\"")
            .append('}')
            .toString();
    }

    private static String nullable(Integer value) {
        return value == null ? "null" : Integer.toString(value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
