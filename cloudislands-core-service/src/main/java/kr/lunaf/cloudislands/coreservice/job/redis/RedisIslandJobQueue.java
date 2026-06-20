package kr.lunaf.cloudislands.coreservice.job.redis;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.job.IslandJobQueue;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;

public final class RedisIslandJobQueue implements IslandJobQueue {
    private static final String GROUP = "cloudislands-agents";
    private final URI redisUri;
    private final Map<UUID, String> streamIdsByJobId = new ConcurrentHashMap<>();
    private final Map<UUID, kr.lunaf.cloudislands.protocol.job.IslandJob> claimedJobs = new ConcurrentHashMap<>();
    private final Map<UUID, String> claimedNodesByJobId = new ConcurrentHashMap<>();
    private final AtomicLong failedJobsTotal = new AtomicLong();
    private final AtomicLong retryAttemptsTotal = new AtomicLong();
    private final AtomicLong redisFailuresTotal = new AtomicLong();

    public RedisIslandJobQueue(URI redisUri) {
        this.redisUri = redisUri;
        ensureGroup();
    }

    public String recoverPending(String nodeId, long minIdleMillis, int maxJobs) {
        try {
            String recovered = new RedisPendingJobRecovery(redisUri, minIdleMillis).claimStale(nodeId, maxJobs);
            retryAttemptsTotal.addAndGet(countStreamIds(recovered));
            return recovered;
        } catch (RuntimeException exception) {
            recordRedisFailure();
            throw exception;
        }
    }

    @Override
    public void publish(IslandJob job) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("XADD", RedisKeys.jobsStream(), "*", "jobId", job.jobId().toString(), "type", job.type().name(), "islandId", job.islandId().toString(), "targetNode", job.targetNode() == null ? "" : job.targetNode(), "priority", Integer.toString(job.priority()), "createdAt", job.createdAt().toString(), "payload", encodePayload(job.payload()));
        } catch (IOException | RuntimeException exception) {
            recordRedisFailure();
            throw new IllegalStateException("failed to publish redis island job", exception);
        }
    }

    @Override
    public List<IslandJob> claim(String nodeId, List<IslandJobType> supportedTypes, int maxJobs) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String reply = redis.command("XREADGROUP", "GROUP", GROUP, nodeId, "COUNT", Integer.toString(maxJobs), "STREAMS", RedisKeys.jobsStream(), ">");
            return parseJobs(redis, reply, nodeId, supportedTypes);
        } catch (IOException | RuntimeException exception) {
            recordRedisFailure();
            throw new IllegalStateException("failed to claim redis island jobs", exception);
        }
    }

    @Override
    public java.util.Optional<kr.lunaf.cloudislands.protocol.job.IslandJob> findClaimed(java.util.UUID jobId) {
        return java.util.Optional.ofNullable(claimedJobs.get(jobId));
    }

    
    public boolean complete(String nodeId, UUID jobId) {
        return ackByJobId(nodeId, jobId, "completed", null);
    }

    @Override
    public boolean fail(String nodeId, UUID jobId, String errorMessage) {
        boolean acked = ackByJobId(nodeId, jobId, "failed", errorMessage);
        if (acked) {
            failedJobsTotal.incrementAndGet();
        }
        return acked;
    }

    @Override
    public boolean retry(UUID jobId) {
        IslandJob job = claimedJobs.get(jobId);
        if (job == null) {
            return false;
        }
        String streamId = streamIdsByJobId.get(jobId);
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("XADD", RedisKeys.jobsStream(), "*", "jobId", job.jobId().toString(), "type", job.type().name(), "islandId", job.islandId().toString(), "targetNode", job.targetNode() == null ? "" : job.targetNode(), "priority", Integer.toString(job.priority()), "createdAt", job.createdAt().toString(), "payload", encodePayload(job.payload()));
            if (streamId != null && !streamId.isBlank()) {
                redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
            }
            claimedJobs.remove(jobId);
            streamIdsByJobId.remove(jobId);
            claimedNodesByJobId.remove(jobId);
            retryAttemptsTotal.incrementAndGet();
            redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_RETRIED", "jobId", jobId.toString(), "streamId", streamId == null ? "" : streamId, "error", "");
            return true;
        } catch (IOException | RuntimeException exception) {
            recordRedisFailure();
            throw new IllegalStateException("failed to retry redis island job", exception);
        }
    }

    @Override
    public boolean cancel(UUID jobId) {
        IslandJob job = claimedJobs.get(jobId);
        String streamId = streamIdsByJobId.get(jobId);
        if (job == null && (streamId == null || streamId.isBlank())) {
            return false;
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            if (streamId != null && !streamId.isBlank()) {
                redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
            }
            claimedJobs.remove(jobId);
            streamIdsByJobId.remove(jobId);
            claimedNodesByJobId.remove(jobId);
            redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_CANCELED", "jobId", jobId.toString(), "streamId", streamId == null ? "" : streamId, "error", "");
            return true;
        } catch (IOException | RuntimeException exception) {
            recordRedisFailure();
            throw new IllegalStateException("failed to cancel redis island job", exception);
        }
    }

    public Map<String, Long> countsByState() {
        Map<String, Long> counts = new HashMap<>();
        counts.put("PENDING", 0L);
        counts.put("CLAIMED", (long) claimedJobs.size());
        counts.put("COMPLETED", 0L);
        counts.put("FAILED", failedJobsTotal.get());
        counts.put("CANCELED", 0L);
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String groups = redis.command("XINFO", "GROUPS", RedisKeys.jobsStream());
            counts.put("PENDING", parseGroupLong(groups, "lag"));
            counts.put("CLAIMED", parseGroupLong(groups, "pending"));
        } catch (IOException | RuntimeException ignored) {
            recordRedisFailure();
            // Keep metrics available from local state when Redis is temporarily unavailable.
        }
        return Map.copyOf(counts);
    }

    public long retryAttemptsTotal() {
        return retryAttemptsTotal.get();
    }

    public long redisFailuresTotal() {
        return redisFailuresTotal.get();
    }

    public double latencySeconds() {
        long start = System.nanoTime();
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("PING");
            return (System.nanoTime() - start) / 1_000_000_000.0D;
        } catch (IOException | RuntimeException ignored) {
            recordRedisFailure();
            return -1.0D;
        }
    }

    private void ensureGroup() {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            redis.command("XGROUP", "CREATE", RedisKeys.jobsStream(), GROUP, "0", "MKSTREAM");
        } catch (Exception ignored) {
            // Existing groups and startup Redis outages are handled by publish/claim paths.
        }
    }

    private boolean ackByJobId(String nodeId, UUID jobId, String state, String errorMessage) {
        String claimedNode = claimedNodesByJobId.get(jobId);
        if (claimedNode == null || !claimedNode.equals(nodeId)) {
            return false;
        }
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            claimedJobs.remove(jobId);
            String streamId = streamIdsByJobId.remove(jobId);
            claimedNodesByJobId.remove(jobId);
            if (streamId != null && !streamId.isBlank()) {
                redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
            }
            redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_" + state.toUpperCase(), "jobId", jobId.toString(), "streamId", streamId == null ? "" : streamId, "error", errorMessage == null ? "" : errorMessage);
            return true;
        } catch (IOException | RuntimeException exception) {
            recordRedisFailure();
            throw new IllegalStateException("failed to ack redis island job", exception);
        }
    }

    private void recordRedisFailure() {
        redisFailuresTotal.incrementAndGet();
    }

    private List<IslandJob> parseJobs(RedisRespConnection redis, String reply, String nodeId, List<IslandJobType> supportedTypes) throws IOException {
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
                appendClaimedJob(redis, streamId, current, nodeId, supportedTypes, jobs);
                streamId = line;
                current.clear();
                continue;
            }
            if (streamId == null) {
                continue;
            }
            if (i + 1 < lines.length) {
                current.put(line, lines[++i]);
            }
        }
        appendClaimedJob(redis, streamId, current, nodeId, supportedTypes, jobs);
        return jobs;
    }

    private void appendClaimedJob(RedisRespConnection redis, String streamId, Map<String, String> current, String nodeId, List<IslandJobType> supportedTypes, List<IslandJob> jobs) throws IOException {
        if (streamId == null || streamId.isBlank() || !current.containsKey("jobId") || !current.containsKey("type") || !current.containsKey("islandId")) {
            return;
        }
        IslandJobType type = safeType(current.get("type"));
        String targetNode = current.getOrDefault("targetNode", "");
        boolean supported = type != null && supportedTypes.contains(type);
        boolean targetMatches = targetNode.isBlank() || targetNode.equals(nodeId);
        if (supported && targetMatches) {
            try {
                UUID jobId = UUID.fromString(current.get("jobId"));
                streamIdsByJobId.put(jobId, streamId);
                IslandJob claimedJob = new IslandJob(jobId, type, UUID.fromString(current.get("islandId")), targetNode, parseInt(current.get("priority"), 0), decodePayload(current.getOrDefault("payload", "")), parseInstant(current.get("createdAt")));
                claimedJobs.put(jobId, claimedJob);
                claimedNodesByJobId.put(jobId, nodeId);
                jobs.add(claimedJob);
            } catch (RuntimeException exception) {
                skipMalformedJob(redis, streamId, current, nodeId, exception);
            }
        } else if (type != null && !targetMatches) {
            requeueMismatchedTarget(redis, streamId, current, nodeId, targetNode);
        } else {
            redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
            redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_SKIPPED_UNSUPPORTED", "jobId", current.getOrDefault("jobId", ""), "streamId", streamId, "nodeId", nodeId, "jobType", current.getOrDefault("type", ""));
        }
    }

    private void skipMalformedJob(RedisRespConnection redis, String streamId, Map<String, String> job, String nodeId, RuntimeException exception) throws IOException {
        if (streamId != null && !streamId.isBlank()) {
            redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
        }
        redis.command(
            "XADD",
            RedisKeys.auditStream(),
            "*",
            "type", "JOB_SKIPPED_MALFORMED",
            "jobId", job.getOrDefault("jobId", ""),
            "streamId", streamId == null ? "" : streamId,
            "nodeId", nodeId,
            "error", exception.getClass().getSimpleName()
        );
    }

    private void requeueMismatchedTarget(RedisRespConnection redis, String streamId, Map<String, String> job, String nodeId, String targetNode) throws IOException {
        redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
        redis.command(
            "XADD",
            RedisKeys.jobsStream(),
            "*",
            "jobId", job.getOrDefault("jobId", ""),
            "type", job.getOrDefault("type", ""),
            "islandId", job.getOrDefault("islandId", ""),
            "targetNode", targetNode == null ? "" : targetNode,
            "priority", job.getOrDefault("priority", "0"),
            "createdAt", job.getOrDefault("createdAt", Instant.now().toString()),
            "payload", job.getOrDefault("payload", "")
        );
        redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_REQUEUED_TARGET_MISMATCH", "jobId", job.getOrDefault("jobId", ""), "streamId", streamId, "nodeId", nodeId, "targetNode", targetNode == null ? "" : targetNode);
    }

    private long parseGroupLong(String reply, String key) {
        if (reply == null || reply.isBlank()) {
            return 0L;
        }
        String[] lines = reply.split("\\R");
        for (int i = 0; i + 1 < lines.length; i++) {
            if (lines[i].equals(key)) {
                try {
                    return Long.parseLong(lines[i + 1]);
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private long countStreamIds(String reply) {
        if (reply == null || reply.isBlank()) {
            return 0L;
        }
        long total = 0L;
        boolean skippedCursor = false;
        for (String line : reply.split("\\R")) {
            if (line.matches("\\d+-\\d+")) {
                if (!skippedCursor) {
                    skippedCursor = true;
                    continue;
                }
                total++;
            }
        }
        return total;
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
