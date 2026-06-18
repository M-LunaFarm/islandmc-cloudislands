package kr.lunaf.cloudislands.common.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import kr.lunaf.cloudislands.common.storage.StorageOutagePolicy;
import org.junit.jupiter.api.Test;

class FailureHandlingPolicyTest {
    @Test
    void pinsNodeDownRecoverySequence() {
        assertEquals(
                "failure-handling-covers-node-down-core-api-down-redis-down-and-object-storage-down",
                FailureHandlingPolicy.CONTRACT
        );
        assertEquals(
                List.of(
                        "heartbeat-timeout",
                        "node-state-down",
                        "block-new-route-to-node",
                        "mark-active-islands-recovery-required",
                        "fallback-players-to-lobby",
                        "core-api-checks-latest-snapshot",
                        "recover-on-another-node-when-safe",
                        "quarantine-when-unsafe"
                ),
                FailureHandlingPolicy.nodeDownSequence()
        );
        assertEquals(
                "players-on-a-down-island-node-fallback-to-lobby-before-recovery",
                FailureHandlingPolicy.PLAYER_FALLBACK_POLICY
        );
        assertEquals(
                "core-api-restores-from-latest-safe-snapshot-or-quarantines-unsafe-islands",
                FailureHandlingPolicy.RECOVERY_SAFETY_POLICY
        );
        assertTrue(FailureHandlingPolicy.nodeDownStep("block-new-route-to-node"));
        assertTrue(FailureHandlingPolicy.nodeDownStep("quarantine-when-unsafe"));
        assertFalse(FailureHandlingPolicy.nodeDownStep("route-to-down-node"));
    }

    @Test
    void alignsCoreApiDownAllowedAndRestrictedOperations() {
        assertTrue(FailureHandlingPolicy.coreApiDownAllowed("active-island-play"));
        assertTrue(FailureHandlingPolicy.coreApiDownAllowed("local-cache-protection"));
        assertTrue(FailureHandlingPolicy.coreApiDownAllowed("basic-teleport-local-fallback"));
        assertTrue(FailureHandlingPolicy.coreApiDownRestricted("new-island-create"));
        assertTrue(FailureHandlingPolicy.coreApiDownRestricted("inactive-island-activation"));
        assertTrue(FailureHandlingPolicy.coreApiDownRestricted("island-move"));
        assertTrue(FailureHandlingPolicy.coreApiDownRestricted("member-change"));
        assertTrue(FailureHandlingPolicy.coreApiDownRestricted("flag-change"));
        assertEquals("현재 섬 서비스 일부 기능이 점검 중입니다.", CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE);
    }

    @Test
    void alignsRedisDownDegradedMode() {
        assertTrue(FailureHandlingPolicy.redisDownAllowed("db-direct-degraded-read"));
        assertTrue(RedisOutagePolicy.durableAuthority("POSTGRESQL"));
        assertTrue(FailureHandlingPolicy.redisDownLimited("cache-performance"));
        assertTrue(FailureHandlingPolicy.redisDownLimited("event-propagation"));
        assertTrue(FailureHandlingPolicy.redisDownLimited("job-queue-processing"));
        assertTrue(FailureHandlingPolicy.redisDownLimited("heartbeat-realtime-visibility"));
        assertEquals("production deployments should run Redis with high availability", RedisOutagePolicy.HA_RECOMMENDATION);
    }

    @Test
    void alignsObjectStorageDownLocalPlayAndRetryLimits() {
        assertTrue(FailureHandlingPolicy.objectStorageDownAllowed("active-island-play"));
        assertTrue(FailureHandlingPolicy.objectStorageDownRestricted("new-activation"));
        assertTrue(FailureHandlingPolicy.objectStorageDownRestricted("island-save"));
        assertTrue(FailureHandlingPolicy.objectStorageDownRestricted("island-snapshot"));
        assertTrue(FailureHandlingPolicy.objectStorageDownRestricted("island-recovery"));
        assertTrue(StorageOutagePolicy.retryQueue("periodic-save"));
        assertTrue(StorageOutagePolicy.retryQueue("empty-island-save"));
    }
}
