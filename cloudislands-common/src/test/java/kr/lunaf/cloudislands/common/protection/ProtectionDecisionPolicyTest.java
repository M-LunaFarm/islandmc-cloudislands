package kr.lunaf.cloudislands.common.protection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionDecisionPolicyTest {
    @Test
    void keepsSynchronousProtectionHotPathCacheOnly() {
        assertEquals("region-index-and-local-permission-cache-only", ProtectionDecisionPolicy.HOT_PATH_POLICY);
        assertEquals("no-core-api-http-database-or-redis-call-on-bukkit-event-thread", ProtectionDecisionPolicy.NO_SYNC_IO_POLICY);
        assertEquals("async-core-event-poller-refreshes-local-cache-outside-protection-decision", ProtectionDecisionPolicy.CACHE_REFRESH_POLICY);
    }

    @Test
    void keepsPermissionDecisionOrderExplicit() {
        assertEquals(
                "admin-bypass>island-owner>explicit-member-role>trusted-override>visitor-flags>default-deny",
                ProtectionDecisionPolicy.DECISION_ORDER
        );
        assertEquals(
                "world-chunk-region-index>bounding-box>island-id>local-permission-cache",
                ProtectionDecisionPolicy.REGION_LOOKUP_ORDER
        );
    }

    @Test
    void namesProtectedEventSurfaceForPaperListeners() {
        assertTrue(ProtectionDecisionPolicy.PROTECTED_EVENT_SURFACE.contains("block-place-break"));
        assertTrue(ProtectionDecisionPolicy.PROTECTED_EVENT_SURFACE.contains("inventory"));
        assertTrue(ProtectionDecisionPolicy.PROTECTED_EVENT_SURFACE.contains("explosion"));
        assertTrue(ProtectionDecisionPolicy.PROTECTED_EVENT_SURFACE.contains("fluid"));
    }
}
