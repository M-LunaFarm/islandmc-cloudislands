package kr.lunaf.cloudislands.common.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FencingTokenTest {
    @Test
    void acceptsOnlyTheCurrentRuntimeOwnerTokenForWrites() {
        FencingToken current = new FencingToken(102L);

        assertTrue(current.accepts(new FencingToken(102L)));
        assertFalse(current.accepts(new FencingToken(101L)));
        assertFalse(current.accepts(new FencingToken(103L)));
        assertEquals("ALLOW_CURRENT_OWNER", current.writeDecision(new FencingToken(102L)));
        assertEquals("DENY_STALE_OWNER", current.writeDecision(new FencingToken(101L)));
        assertEquals("DENY_STALE_OWNER", current.writeDecision(null));
        assertEquals("DENY_FUTURE_TOKEN_WITHOUT_ROW_LOCK", current.writeDecision(new FencingToken(103L)));
    }

    @Test
    void allocatesNextOwnerTokenFromAuthoritativeCurrentToken() {
        assertEquals(new FencingToken(0L), FencingToken.initial());
        assertEquals(new FencingToken(1L), FencingToken.nextAfter(null));
        assertEquals(new FencingToken(103L), FencingToken.nextAfter(new FencingToken(102L)));
        assertTrue(new FencingToken(102L).stale(new FencingToken(101L)));
        assertFalse(new FencingToken(102L).stale(new FencingToken(102L)));
        assertFalse(new FencingToken(102L).stale(new FencingToken(103L)));
    }

    @Test
    void documentsRedisAsAdvisoryAndDatabaseFencingAsAuthoritative() {
        assertEquals(
                "current-fencing-token-required-before-snapshot-or-runtime-write",
                FencingToken.WRITE_POLICY
        );
        assertEquals(
                "new-runtime-owner-uses-current-token-plus-one-after-row-lock",
                FencingToken.NEXT_TOKEN_POLICY
        );
        assertEquals(
                "redis-lock-is-advisory-postgresql-row-lock-and-fencing-token-are-authoritative",
                FencingToken.REDIS_LOCK_POLICY
        );
        assertEquals(
                "island-1-token-101-fails-after-island-2-row-lock-advances-current-token-to-102",
                FencingToken.FAILURE_RECOVERY_SCENARIO
        );
        assertEquals(
                "redis-lock-fast-duplicate-filter-then-postgresql-row-lock-then-fencing-token-increment",
                FencingToken.ACQUIRE_ORDER_POLICY
        );
        assertEquals("redis-lock>postgresql-row-lock>fencing-token-next-owner", FencingToken.acquisitionSteps());
    }

    @Test
    void rejectsLateSnapshotWriteFromRecoveredOldNode() {
        FencingToken island1Lease = new FencingToken(101L);
        FencingToken island2Lease = FencingToken.nextAfter(island1Lease);

        assertEquals(new FencingToken(102L), island2Lease);
        assertTrue(island2Lease.rejectsStaleSnapshotWrite(island1Lease));
        assertEquals("DENY_STALE_OWNER", island2Lease.writeDecision(island1Lease));
        assertEquals("ALLOW_CURRENT_OWNER", island2Lease.writeDecision(new FencingToken(102L)));
    }
}
