package kr.lunaf.cloudislands.common.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RedisKeyspacePolicyTest {
    @Test
    void pinsRequiredRedisKeyPatternsFromTheDesign() {
        assertEquals(
            List.of(
                "ci:server:{nodeId}:heartbeat",
                "ci:server:{nodeId}:state",
                "ci:server:{nodeId}:metrics",
                "ci:player:{uuid}:island",
                "ci:player:{uuid}:route-ticket",
                "ci:player:{uuid}:session",
                "ci:island:{islandId}:summary",
                "ci:island:{islandId}:runtime",
                "ci:island:{islandId}:members",
                "ci:island:{islandId}:flags",
                "ci:island:{islandId}:permissions",
                "ci:island:{islandId}:warps",
                "ci:lock:player-create:{uuid}",
                "ci:lock:island:{islandId}",
                "ci:lock:activation:{islandId}",
                "ci:stream:jobs",
                "ci:stream:events",
                "ci:stream:audit"
            ),
            RedisKeyspacePolicy.requiredKeyPatterns()
        );
        assertTrue(RedisKeyspacePolicy.requiredKeyPattern("ci:server:{nodeId}:heartbeat"));
        assertTrue(RedisKeyspacePolicy.requiredKeyPattern(" ci:stream:events "));
        assertFalse(RedisKeyspacePolicy.requiredKeyPattern("ci:source-of-truth:islands"));
    }

    @Test
    void pinsRedisTtlsFromTheDesign() {
        assertEquals(
            Map.of(
                "server-heartbeat", 5_000L,
                "route-ticket-cache", 30_000L,
                "player-island-cache", 300_000L,
                "island-summary-cache", 60_000L,
                "permissions-cache", 30_000L,
                "lock-min", 10_000L,
                "lock-max", 60_000L
            ),
            RedisKeyspacePolicy.requiredTtlsMillis()
        );
        assertEquals(5_000L, RedisKeyspacePolicy.ttlMillis("server-heartbeat"));
        assertEquals(300_000L, RedisKeyspacePolicy.ttlMillis(" PLAYER-ISLAND-CACHE "));
        assertEquals(60_000L, RedisKeyspacePolicy.ttlMillis("lock-max"));
        assertEquals(-1L, RedisKeyspacePolicy.ttlMillis("forever-authoritative-cache"));
    }

    @Test
    void recordsRedisAuthorityBoundaries() {
        assertEquals(
            "redis-is-cache-lock-stream-and-fast-state-only-postgresql-transaction-and-fencing-token-are-authoritative",
            RedisKeyspacePolicy.SOURCE_OF_TRUTH_POLICY
        );
        assertEquals("redis-locks-are-advisory-never-final-write-authority", RedisKeyspacePolicy.LOCK_AUTHORITY_POLICY);
        assertEquals("redis-streams-carry-jobs-events-and-audit-as-append-only-delivery-log", RedisKeyspacePolicy.STREAM_POLICY);
        assertTrue(RedisKeyspacePolicy.keyPatternSummary().contains("ci:stream:audit"));
    }
}
