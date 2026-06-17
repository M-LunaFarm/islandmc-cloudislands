package kr.lunaf.cloudislands.common.failure;

import java.util.Set;

public final class RedisOutagePolicy {
    public static final String CONTRACT = "redis-down-keeps-postgresql-authoritative-and-enters-db-direct-degraded-mode";
    public static final String SOURCE_OF_TRUTH_POLICY = "PostgreSQL remains the source of truth when Redis is unavailable";
    public static final String DB_DIRECT_READ_POLICY = "Core service may bypass Redis cache and query PostgreSQL directly in degraded mode";
    public static final String CACHE_PERFORMANCE_POLICY = "cache performance is degraded until Redis recovers";
    public static final String EVENT_PROPAGATION_DELAY_POLICY = "global event propagation may be delayed while Redis streams are unavailable";
    public static final String JOB_QUEUE_LIMIT_POLICY = "new job queue processing is restricted while Redis streams are unavailable";
    public static final String HEARTBEAT_REALTIME_LIMIT_POLICY = "heartbeat realtime visibility is reduced while Redis cache writes fail";
    public static final String HA_RECOMMENDATION = "production deployments should run Redis with high availability";

    public static final Set<String> LIMITED_CAPABILITIES = Set.of(
        "cache-performance",
        "event-propagation",
        "job-queue-processing",
        "heartbeat-realtime-visibility"
    );

    public static final Set<String> FAILURE_METRIC_KEYS = Set.of(
        "redisCacheFailuresTotal",
        "redisEventPublishFailuresTotal",
        "redisJobQueueFailuresTotal",
        "redisHeartbeatCacheFailuresTotal"
    );

    private RedisOutagePolicy() {
    }

    public static boolean limitedCapability(String capability) {
        return capability != null && LIMITED_CAPABILITIES.contains(capability);
    }

    public static boolean redisFailureMetric(String key) {
        return key != null && FAILURE_METRIC_KEYS.contains(key);
    }
}
