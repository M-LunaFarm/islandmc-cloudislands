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
    }
}
