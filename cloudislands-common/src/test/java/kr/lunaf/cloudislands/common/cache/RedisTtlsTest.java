package kr.lunaf.cloudislands.common.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedisTtlsTest {
    @Test
    void keepsGoalCacheTtlsStable() {
        assertEquals(5_000L, RedisTtls.SERVER_HEARTBEAT_MILLIS);
        assertEquals(30_000L, RedisTtls.ROUTE_TICKET_MILLIS);
        assertEquals(300_000L, RedisTtls.PLAYER_ISLAND_MILLIS);
        assertEquals(60_000L, RedisTtls.ISLAND_SUMMARY_MILLIS);
        assertEquals(30_000L, RedisTtls.ISLAND_PERMISSIONS_MILLIS);
    }

    @Test
    void keepsDistributedLockTtlWithinShortLeaseWindow() {
        assertEquals(10_000L, RedisTtls.LOCK_MIN_MILLIS);
        assertEquals(60_000L, RedisTtls.LOCK_MAX_MILLIS);
        assertTrue(RedisTtls.LOCK_MIN_MILLIS <= RedisTtls.ROUTE_TICKET_MILLIS);
        assertTrue(RedisTtls.LOCK_MAX_MILLIS >= RedisTtls.ROUTE_TICKET_MILLIS);
    }
}
