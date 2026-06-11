package kr.lunaf.cloudislands.coreservice.job.redis;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class RedisIslandJobQueue implements IslandJobQueue {
    private static final String GROUP = "cloudislands-agents";
    private final URI redisUri;
    private final Map<UUID, String> streamIdsByJobId = new ConcurrentHashMap<>();

    public RedisIslandJobQueue(URI redisUri) {
        this.redisUri = redisUri;
        ensureGroup();
    }

    @Override
    public void publish(IslandJob job) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("XADD", RedisKeys.jobsStream(), "*", "jobId", job.jobId().toString(), "type", job.type().name(), "islandId", job.islandId().toString(), "targetNode", job.targetNode() == null ? "" : job.targetNode(), "priority", Integer.toString(job.priority()), "createdAt", job.createdAt().toString(), "payload", encodePayload(job.payload()));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to publish redis island job", exception);
        }
    }

    @Override
    public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String reply = redis.command("XREADGROUP", "GROUP", GROUP, nodeId, "COUNT", Integer.toString(maxJobs), "STREAMS", RedisKeys.jobsStream(), ">");
            return parseJobs(reply, nodeId, supportedTypes);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to claim redis island jobs", exception);
        }
    }

    @Override
    public void complete(String nodeId, UUID jobId) {
        ackByJobId(jobId, "completed", null);
    }

    @Override
    public void fail(String nodeId, UUID jobId, String errorMessage) {
        ackByJobId(jobId, "failed", errorMessage);
    }

    private void ensureGroup() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("XGROUP", "CREATE", RedisKeys.jobsStream(), GROUP, "0", "MKSTREAM");
        } catch (Exception ignored) {
            // Existing groups and startup Redis outages are handled by publish/claim paths.
        }
    }

    private void ackByJobId(UUID jobId, String state, String errorMessage) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String streamId = streamIdsByJobId.remove(jobId);
            if (streamId != null && !streamId.isBlank()) {
                redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
            }
            redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_" + state.toUpperCase(), "jobId", jobId.toString(), "streamId", streamId == null ? "" : streamId, "error", errorMessage == null ? "" : errorMessage);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to ack redis island job", exception);
        }
    }

    private List<IslandJob> parseJobs(String reply, String nodeId, List<IslandJobType> supportedTypes) {
        List<IslandJob> jobs = new ArrayList<>();
        if (reply == null || reply.isBlank()) {
            return jobs;
        }
        String[] lines = reply.split("\\R");
        Map<String, String> current = new java.util.HashMap<>();
        String streamId = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.matches("\\d+-\\d+")) {
                streamId = line;
                current.clear();
                continue;
            }
            if (i + 1 < lines.length) {
                current.put(line, lines[++i]);
            }
            if (current.containsKey("jobId") && current.containsKey("type") && current.containsKey("islandId")) {
                IslandJobType type = safeType(current.get("type"));
                String targetNode = current.getOrDefault("targetNode", "");
                if (type != null && supportedTypes.contains(type) && (targetNode.isBlank() || targetNode.equals(nodeId))) {
                    UUID jobId = UUID.fromString(current.get("jobId"));
                    if (streamId != null) {
                        streamIdsByJobId.put(jobId, streamId);
                    }
                    jobs.add(new IslandJob(jobId, type, UUID.fromString(current.get("islandId")), targetNode, parseInt(current.get("priority"), 0), decodePayload(current.getOrDefault("payload", "")), parseInstant(current.get("createdAt"))));
                }
                current.clear();
            }
        }
        return jobs;
    }

    private IslandJobType safeType(String value) {
        try {
            return IslandJobType.valueOf(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }

    private String encodePayload(Map<String, String> payload) {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (!builder.isEmpty()) {
                builder.append(';');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue().replace(";", "%3B").replace("=", "%3D"));
        }
        return builder.toString();
    }

    private Map<String, String> decodePayload(String encoded) {
        Map<String, String> values = new java.util.HashMap<>();
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        for (String part : encoded.split(";")) {
            int equals = part.indexOf('=');
            if (equals > 0) {
                values.put(part.substring(0, equals), part.substring(equals + 1).replace("%3D", "=").replace("%3B", ";"));
            }
        }
        return Map.copyOf(values);
    }
}
