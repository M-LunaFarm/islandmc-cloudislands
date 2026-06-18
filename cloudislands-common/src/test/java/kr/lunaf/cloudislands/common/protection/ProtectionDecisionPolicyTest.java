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
        assertEquals(
                "synchronous-paper-events-may-read-region-index-permission-cache-and-runtime-cache-only",
                ProtectionDecisionPolicy.SYNC_EVENT_SOURCE_POLICY
        );
        assertEquals(
                "core-api-http-database-and-redis-refresh-local-cache-outside-event-thread",
                ProtectionDecisionPolicy.ASYNC_REFRESH_SOURCE_POLICY
        );
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

    @Test
    void allowsOnlyLocalSourcesOnSynchronousPaperEvents() {
        assertTrue(ProtectionDecisionPolicy.syncAllowedSources().contains("region-index"));
        assertTrue(ProtectionDecisionPolicy.syncAllowedSources().contains("local-permission-cache"));
        assertTrue(ProtectionDecisionPolicy.syncAllowedSources().contains("local-runtime-cache"));
        assertEquals("ALLOW_LOCAL_CACHE", ProtectionDecisionPolicy.syncSourceDecision("region_index"));
        assertEquals("ALLOW_LOCAL_CACHE", ProtectionDecisionPolicy.syncSourceDecision("local-permission-cache"));
    }

    @Test
    void rejectsNetworkAndStorageSourcesOnSynchronousPaperEvents() {
        assertTrue(ProtectionDecisionPolicy.syncForbiddenSources().contains("core-api-http"));
        assertTrue(ProtectionDecisionPolicy.syncForbiddenSources().contains("database"));
        assertTrue(ProtectionDecisionPolicy.syncForbiddenSources().contains("redis"));
        assertEquals("DENY_SYNC_IO", ProtectionDecisionPolicy.syncSourceDecision("core_api_http"));
        assertEquals("DENY_SYNC_IO", ProtectionDecisionPolicy.syncSourceDecision("postgresql"));
        assertEquals("DENY_SYNC_IO", ProtectionDecisionPolicy.syncSourceDecision("redis"));
        assertEquals("DENY_UNKNOWN_SOURCE", ProtectionDecisionPolicy.syncSourceDecision("external-cache"));
    }
}
