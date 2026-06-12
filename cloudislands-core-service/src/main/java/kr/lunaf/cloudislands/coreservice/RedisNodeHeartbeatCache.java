package kr.lunaf.cloudislands.coreservice;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class RedisNodeHeartbeatCache {
    private final URI redisUri;
    private final long ttlMillis;
    private final boolean enabled;
    private final AtomicLong failures = new AtomicLong();

    public RedisNodeHeartbeatCache(URI redisUri, Duration heartbeatTimeout, boolean enabled) {
        this.redisUri = redisUri;
        this.ttlMillis = Math.max(1_000L, heartbeatTimeout.toMillis() * 3L);
        this.enabled = enabled;
    }

    public void record(NodeHeartbeatRequest heartbeat) {
        if (!enabled) {
            return;
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("SET", RedisKeys.serverHeartbeat(heartbeat.nodeId()), heartbeatJson(heartbeat), "PX", Long.toString(ttlMillis));
            redis.command("SET", RedisKeys.serverState(heartbeat.nodeId()), heartbeat.state().name(), "PX", Long.toString(ttlMillis));
        } catch (IOException | RuntimeException ignored) {
            failures.incrementAndGet();
        }
    }

    public long failuresTotal() {
        return failures.get();
    }

    private static String heartbeatJson(NodeHeartbeatRequest heartbeat) {
        return new StringBuilder("{")
            .append("\"nodeId\":\"").append(escape(heartbeat.nodeId())).append("\",")
            .append("\"pool\":\"").append(escape(heartbeat.pool() == null || heartbeat.pool().isBlank() ? "island" : heartbeat.pool())).append("\",")
            .append("\"server\":\"").append(escape(heartbeat.velocityServerName())).append("\",")
            .append("\"nodeVersion\":\"").append(escape(heartbeat.nodeVersion())).append("\",")
            .append("\"state\":\"").append(heartbeat.state().name()).append("\",")
            .append("\"players\":").append(heartbeat.players()).append(',')
            .append("\"softPlayerCap\":").append(heartbeat.softPlayerCap()).append(',')
            .append("\"hardPlayerCap\":").append(heartbeat.hardPlayerCap()).append(',')
            .append("\"reservedSlots\":").append(heartbeat.reservedSlots()).append(',')
            .append("\"activeIslands\":").append(heartbeat.activeIslands()).append(',')
            .append("\"maxActiveIslands\":").append(heartbeat.maxActiveIslands()).append(',')
            .append("\"mspt\":").append(heartbeat.mspt()).append(',')
            .append("\"activationQueue\":").append(heartbeat.activationQueue()).append(',')
            .append("\"maxActivationQueue\":").append(heartbeat.maxActivationQueue()).append(',')
            .append("\"chunkLoadPressure\":").append(heartbeat.chunkLoadPressure()).append(',')
            .append("\"heapUsedMb\":").append(heartbeat.heapUsedMb()).append(',')
            .append("\"heapMaxMb\":").append(heartbeat.heapMaxMb()).append(',')
            .append("\"recentFailurePenalty\":").append(heartbeat.recentFailurePenalty()).append(',')
            .append("\"storageAvailable\":").append(heartbeat.storageAvailable()).append(',')
            .append("\"supportedTemplates\":\"").append(escape(heartbeat.supportedTemplates())).append("\"")
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
