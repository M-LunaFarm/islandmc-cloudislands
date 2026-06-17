package kr.lunaf.cloudislands.coreservice.job.redis;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.coreservice.redis.RedisRespConnection;

public final class RedisPendingJobRecovery {
    private static final String GROUP = "cloudislands-agents";
    private final URI redisUri;
    private final long minIdleMillis;

    public RedisPendingJobRecovery(URI redisUri, long minIdleMillis) {
        this.redisUri = redisUri;
        this.minIdleMillis = minIdleMillis;
    }

    public String claimStale(String newOwner, int maxJobs) {
        try (RedisRespConnection redis = new RedisRespConnection(redisUri)) {
            String recovered = redis.command("XAUTOCLAIM", RedisKeys.jobsStream(), GROUP, newOwner, Long.toString(minIdleMillis), "0-0", "COUNT", Integer.toString(maxJobs));
            requeueRecovered(redis, recovered);
            return recovered;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("failed to recover stale redis jobs", exception);
        }
    }

    private void requeueRecovered(RedisRespConnection redis, String reply) throws IOException {
        if (reply == null || reply.isBlank()) {
            return;
        }
        String[] lines = reply.split("\\R");
        boolean skippedCursor = false;
        String streamId = "";
        Map<String, String> current = new HashMap<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.matches("\\d+-\\d+")) {
                if (!skippedCursor) {
                    skippedCursor = true;
                    continue;
                }
                requeue(redis, streamId, current);
                streamId = line;
                current.clear();
                continue;
            }
            if (i + 1 < lines.length) {
                current.put(line, lines[++i]);
            }
        }
        requeue(redis, streamId, current);
    }

    private void requeue(RedisRespConnection redis, String streamId, Map<String, String> job) throws IOException {
        if (streamId == null || streamId.isBlank()) {
            return;
        }
        if (!hasRequiredFields(job)) {
            skipMalformedRecovered(redis, streamId, job);
            return;
        }
        redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
        redis.command(
            "XADD",
            RedisKeys.jobsStream(),
            "*",
            "jobId", job.getOrDefault("jobId", ""),
            "type", job.getOrDefault("type", ""),
            "islandId", job.getOrDefault("islandId", ""),
            "targetNode", job.getOrDefault("targetNode", ""),
            "priority", job.getOrDefault("priority", "0"),
            "createdAt", job.getOrDefault("createdAt", Instant.now().toString()),
            "payload", job.getOrDefault("payload", "")
        );
        redis.command("XADD", RedisKeys.auditStream(), "*", "type", "JOB_RECOVERED_REQUEUED", "jobId", job.getOrDefault("jobId", ""), "streamId", streamId);
    }

    private void skipMalformedRecovered(RedisRespConnection redis, String streamId, Map<String, String> job) throws IOException {
        redis.command("XACK", RedisKeys.jobsStream(), GROUP, streamId);
        redis.command(
            "XADD",
            RedisKeys.auditStream(),
            "*",
            "type", "JOB_RECOVERED_SKIPPED_MALFORMED",
            "jobId", job.getOrDefault("jobId", ""),
            "streamId", streamId
        );
    }

    private boolean hasRequiredFields(Map<String, String> job) {
        return validUuid(job.get("jobId")) && present(job, "type") && validUuid(job.get("islandId"));
    }

    private boolean present(Map<String, String> job, String key) {
        String value = job.get(key);
        return value != null && !value.isBlank();
    }

    private boolean validUuid(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
