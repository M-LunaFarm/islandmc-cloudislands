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
        assertTrue(ProtectionDecisionPolicy.protectedEvents().contains("BlockBreakEvent"));
        assertTrue(ProtectionDecisionPolicy.protectedEvents().contains("PlayerInteractEvent"));
        assertTrue(ProtectionDecisionPolicy.protectedEvents().contains("EntityExplodeEvent"));
        assertTrue(ProtectionDecisionPolicy.protectedEvents().contains("FluidLevelChangeEvent"));
        assertTrue(ProtectionDecisionPolicy.protectedEvent("block_break_event"));
        assertTrue(ProtectionDecisionPolicy.protectedEvent("BlockFromToEvent"));
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

    @Test
    void pinsBorderHandlingByPlayerRole() {
        assertEquals(
                "visitor-returns-to-visitor-spawn-member-returns-to-island-spawn-admin-may-bypass",
                ProtectionDecisionPolicy.BORDER_POLICY
        );
        assertEquals("TELEPORT_VISITOR_SPAWN", ProtectionDecisionPolicy.borderAction("VISITOR"));
        assertEquals("TELEPORT_VISITOR_SPAWN", ProtectionDecisionPolicy.borderAction("BANNED"));
        assertEquals("TELEPORT_ISLAND_SPAWN", ProtectionDecisionPolicy.borderAction("MEMBER"));
        assertEquals("TELEPORT_ISLAND_SPAWN", ProtectionDecisionPolicy.borderAction("TRUSTED"));
        assertEquals("ALLOW_BYPASS", ProtectionDecisionPolicy.borderAction("ADMIN"));
        assertEquals("BLOCK_OR_RETURN_TO_SAFE_SPAWN", ProtectionDecisionPolicy.borderAction("unknown"));
    }
}
