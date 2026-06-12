package kr.lunaf.cloudislands.coreservice;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class CachingNodeRegistry implements NodeRegistry {
    private final NodeRegistry delegate;
    private final URI redisUri;
    private final long heartbeatTtlMillis;
    private final AtomicLong failures = new AtomicLong();

    public CachingNodeRegistry(NodeRegistry delegate, URI redisUri, Duration heartbeatTimeout) {
        this.delegate = delegate;
        this.redisUri = redisUri;
        this.heartbeatTtlMillis = Math.max(1_000L, heartbeatTimeout.toMillis() * 3L);
    }

    @Override
    public void heartbeat(NodeHeartbeatRequest request) {
        delegate.heartbeat(request);
        delegate.find(request.nodeId()).ifPresent(this::cache);
    }

    @Override
    public boolean drain(String nodeId) {
        boolean changed = delegate.drain(nodeId);
        if (changed) {
            delegate.find(nodeId).ifPresent(this::cache);
        }
        return changed;
    }

    @Override
    public boolean shutdownSafe(String nodeId) {
        boolean changed = delegate.shutdownSafe(nodeId);
        if (changed) {
            delegate.find(nodeId).ifPresent(this::cache);
        }
        return changed;
    }

    @Override
    public boolean undrain(String nodeId) {
        boolean changed = delegate.undrain(nodeId);
        if (changed) {
            delegate.find(nodeId).ifPresent(this::cache);
        }
        return changed;
    }

    @Override
    public List<String> markStaleDown(Duration heartbeatTimeout) {
        List<String> down = delegate.markStaleDown(heartbeatTimeout);
        for (String nodeId : down) {
            delegate.find(nodeId).ifPresent(this::cache);
        }
        return down;
    }

    @Override
    public List<NodeLoad> snapshot() {
        List<NodeLoad> nodes = delegate.snapshot();
        nodes.forEach(this::cache);
        return nodes;
    }

    @Override
    public Optional<NodeLoad> find(String nodeId) {
        Optional<NodeLoad> node = delegate.find(nodeId);
        node.ifPresent(this::cache);
        return node;
    }

    public long failuresTotal() {
        return failures.get();
    }

    private void cache(NodeLoad node) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.serverHeartbeat(node.nodeId()), NodeRegistry.toJson(node), "PX", Long.toString(heartbeatTtlMillis));
            redis.command("SET", RedisKeys.serverState(node.nodeId()), node.state().name());
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }
}
