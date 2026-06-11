package kr.lunaf.cloudislands.coreservice.job.redis;

import java.io.IOException;
import java.net.URI;
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
            return redis.command("XAUTOCLAIM", RedisKeys.jobsStream(), GROUP, newOwner, Long.toString(minIdleMillis), "0-0", "COUNT", Integer.toString(maxJobs));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to recover stale redis jobs", exception);
        }
    }
}
