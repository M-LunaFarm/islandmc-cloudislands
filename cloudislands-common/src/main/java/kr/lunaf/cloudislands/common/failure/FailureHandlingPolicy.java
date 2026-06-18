package kr.lunaf.cloudislands.common.failure;

import java.util.List;
import java.util.Set;
import kr.lunaf.cloudislands.common.storage.StorageOutagePolicy;

public final class FailureHandlingPolicy {
    public static final String CONTRACT = "failure-handling-covers-node-down-core-api-down-redis-down-and-object-storage-down";
    public static final String PLAYER_FALLBACK_POLICY = "players-on-a-down-island-node-fallback-to-lobby-before-recovery";
    public static final String RECOVERY_SAFETY_POLICY = "core-api-restores-from-latest-safe-snapshot-or-quarantines-unsafe-islands";

    private static final List<String> NODE_DOWN_SEQUENCE = List.of(
            "heartbeat-timeout",
            "node-state-down",
            "block-new-route-to-node",
            "mark-active-islands-recovery-required",
            "fallback-players-to-lobby",
            "core-api-checks-latest-snapshot",
            "recover-on-another-node-when-safe",
            "quarantine-when-unsafe"
    );

    private static final Set<String> CORE_API_DOWN_ALLOWED = Set.of(
            "active-island-play",
            "local-cache-protection",
            "basic-teleport-local-fallback"
    );

    private static final Set<String> REDIS_DOWN_ALLOWED = Set.of(
            "db-direct-degraded-read"
    );

    private FailureHandlingPolicy() {
    }

    public static List<String> nodeDownSequence() {
        return NODE_DOWN_SEQUENCE;
    }

    public static boolean nodeDownStep(String step) {
        return step != null && NODE_DOWN_SEQUENCE.contains(step);
    }

    public static boolean coreApiDownAllowed(String operation) {
        return operation != null && CORE_API_DOWN_ALLOWED.contains(operation);
    }

    public static boolean coreApiDownRestricted(String operation) {
        return CoreApiDegradedModePolicy.restrictedOperation(operation);
    }

    public static boolean redisDownAllowed(String operation) {
        return operation != null && REDIS_DOWN_ALLOWED.contains(operation);
    }

    public static boolean redisDownLimited(String capability) {
        return RedisOutagePolicy.limitedCapability(capability);
    }

    public static boolean objectStorageDownAllowed(String operation) {
        return StorageOutagePolicy.allowedDuringOutage(operation);
    }

    public static boolean objectStorageDownRestricted(String operation) {
        return StorageOutagePolicy.restrictedDuringOutage(operation);
    }
}
