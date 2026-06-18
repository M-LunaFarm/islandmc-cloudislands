package kr.lunaf.cloudislands.common.cache;

import java.util.List;
import java.util.Map;

public final class RedisKeyspacePolicy {
    public static final String SOURCE_OF_TRUTH_POLICY = "redis-is-cache-lock-stream-and-fast-state-only-postgresql-transaction-and-fencing-token-are-authoritative";
    public static final String LOCK_AUTHORITY_POLICY = "redis-locks-are-advisory-never-final-write-authority";
    public static final String STREAM_POLICY = "redis-streams-carry-jobs-events-and-audit-as-append-only-delivery-log";

    private static final List<String> REQUIRED_KEY_PATTERNS = List.of(
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
    );

    private static final Map<String, Long> REQUIRED_TTLS_MILLIS = Map.of(
        "server-heartbeat", 5_000L,
        "route-ticket-cache", 30_000L,
        "player-island-cache", 300_000L,
        "island-summary-cache", 60_000L,
        "permissions-cache", 30_000L,
        "lock-min", 10_000L,
        "lock-max", 60_000L
    );

    private RedisKeyspacePolicy() {
    }

    public static List<String> requiredKeyPatterns() {
        return REQUIRED_KEY_PATTERNS;
    }

    public static boolean requiredKeyPattern(String pattern) {
        return pattern != null && REQUIRED_KEY_PATTERNS.contains(pattern.trim());
    }

    public static Map<String, Long> requiredTtlsMillis() {
        return REQUIRED_TTLS_MILLIS;
    }

    public static long ttlMillis(String key) {
        if (key == null) {
            return -1L;
        }
        return REQUIRED_TTLS_MILLIS.getOrDefault(key.trim().toLowerCase(), -1L);
    }

    public static String keyPatternSummary() {
        return String.join(",", REQUIRED_KEY_PATTERNS);
    }
}
