package kr.lunaf.cloudislands.common.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CoreApiDegradedModePolicyTest {
    @Test
    void documentsAllowedLocalBehaviorWhenCoreApiIsDown() {
        assertEquals(
            "core-api-down-keeps-loaded-islands-playable-and-blocks-control-plane-writes",
            CoreApiDegradedModePolicy.CONTRACT
        );
        assertEquals(
            "loaded active islands continue play on the current Paper node",
            CoreApiDegradedModePolicy.ACTIVE_ISLAND_PLAY_POLICY
        );
        assertEquals(
            "local protection cache remains authoritative for already loaded islands",
            CoreApiDegradedModePolicy.LOCAL_CACHE_PROTECTION_POLICY
        );
        assertEquals(
            "basic teleport may use local home fallback while Core API is unavailable",
            CoreApiDegradedModePolicy.BASIC_TELEPORT_POLICY
        );
    }

    @Test
    void marksControlPlaneWritesAsRestricted() {
        assertTrue(CoreApiDegradedModePolicy.restrictedOperation("new-island-create"));
        assertTrue(CoreApiDegradedModePolicy.restrictedOperation("inactive-island-activation"));
        assertTrue(CoreApiDegradedModePolicy.restrictedOperation("island-move"));
        assertTrue(CoreApiDegradedModePolicy.restrictedOperation("member-change"));
        assertTrue(CoreApiDegradedModePolicy.restrictedOperation("flag-change"));
        assertFalse(CoreApiDegradedModePolicy.restrictedOperation("active-island-play"));
        assertFalse(CoreApiDegradedModePolicy.restrictedOperation(null));
    }

    @Test
    void keepsPlayerFacingMaintenanceMessagesStable() {
        assertEquals("현재 섬 서비스 일부 기능이 점검 중입니다.", CoreApiDegradedModePolicy.MAINTENANCE_MESSAGE);
        assertEquals(
            "현재 섬 서비스 일부 기능이 점검 중이라 기본 홈 위치로 이동합니다.",
            CoreApiDegradedModePolicy.HOME_FALLBACK_MESSAGE
        );
    }

    @Test
    void mapsCoreWriteRejectionCodesToDegradedMode() {
        assertTrue(CoreApiDegradedModePolicy.coreWriteRejected("JOB_QUEUE_UNAVAILABLE"));
        assertTrue(CoreApiDegradedModePolicy.coreWriteRejected("RECOVERY_UNAVAILABLE"));
        assertFalse(CoreApiDegradedModePolicy.coreWriteRejected("ISLAND_NOT_FOUND"));
        assertFalse(CoreApiDegradedModePolicy.coreWriteRejected(null));
    }
}
