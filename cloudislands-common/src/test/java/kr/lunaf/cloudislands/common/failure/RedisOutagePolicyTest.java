package kr.lunaf.cloudislands.common.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedisOutagePolicyTest {
    @Test
    void documentsPostgresqlAsSourceOfTruthDuringRedisOutage() {
        assertEquals(
            "redis-down-keeps-core-jdbc-authoritative-and-enters-db-direct-degraded-mode",
            RedisOutagePolicy.CONTRACT
        );
        assertEquals(
            "PostgreSQL, MySQL, or MariaDB Core JDBC remains the source of truth when Redis is unavailable",
            RedisOutagePolicy.SOURCE_OF_TRUTH_POLICY
        );
        assertEquals(
            "Core service may bypass Redis cache and query Core JDBC directly in degraded mode",
            RedisOutagePolicy.DB_DIRECT_READ_POLICY
        );
        assertEquals(
            "redis-is-discardable-cache-and-stream-transport-not-island-state-authority",
            RedisOutagePolicy.DURABLE_BACKEND_POLICY
        );
    }

    @Test
    void treatsPostgresqlMysqlAndMariadbAsDurableAuthorities() {
        assertTrue(RedisOutagePolicy.durableAuthority("POSTGRESQL"));
        assertTrue(RedisOutagePolicy.durableAuthority("mysql"));
        assertTrue(RedisOutagePolicy.durableAuthority(" MariaDB "));
        assertFalse(RedisOutagePolicy.durableAuthority("REDIS"));
        assertFalse(RedisOutagePolicy.durableAuthority("CORE_API"));
        assertFalse(RedisOutagePolicy.durableAuthority(null));
    }

    @Test
    void marksOnlyRedisBackedCapabilitiesAsLimited() {
        assertTrue(RedisOutagePolicy.limitedCapability("cache-performance"));
        assertTrue(RedisOutagePolicy.limitedCapability("event-propagation"));
        assertTrue(RedisOutagePolicy.limitedCapability("job-queue-processing"));
        assertTrue(RedisOutagePolicy.limitedCapability("heartbeat-realtime-visibility"));
        assertFalse(RedisOutagePolicy.limitedCapability("postgresql-writes"));
        assertFalse(RedisOutagePolicy.limitedCapability(null));
    }

    @Test
    void keepsRedisFailureMetricsNamed() {
        assertTrue(RedisOutagePolicy.redisFailureMetric("redisCacheFailuresTotal"));
        assertTrue(RedisOutagePolicy.redisFailureMetric("redisEventPublishFailuresTotal"));
        assertTrue(RedisOutagePolicy.redisFailureMetric("redisJobQueueFailuresTotal"));
        assertTrue(RedisOutagePolicy.redisFailureMetric("redisHeartbeatCacheFailuresTotal"));
        assertFalse(RedisOutagePolicy.redisFailureMetric("postgresqlFailuresTotal"));
        assertFalse(RedisOutagePolicy.redisFailureMetric(null));
    }

    @Test
    void recommendsHighAvailabilityForProductionRedis() {
        assertEquals(
            "database-fencing-and-row-state-remain-authoritative-when-redis-locks-are-unavailable",
            RedisOutagePolicy.LOCK_FALLBACK_POLICY
        );
        assertEquals(
            "production deployments should run Redis with high availability",
            RedisOutagePolicy.HA_RECOMMENDATION
        );
    }
}
