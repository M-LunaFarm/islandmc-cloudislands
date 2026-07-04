package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisRuntimeAuthorityTest {
    @Test
    void localOwnerCanTickAndWriteWhenActive() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000007101");
        SatisRuntimeAuthority authority = new SatisRuntimeAuthority("node-a");

        authority.activated(islandId, "node-a", "island_world", 2, 3);

        assertTrue(authority.canTick(islandId));
        assertTrue(authority.canWrite(islandId));
        assertTrue(authority.canWrite(islandId, 0L));
        assertEquals(0L, authority.snapshot(islandId).fencingToken());
    }

    @Test
    void fencingTokenMismatchBlocksStaleWrites() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000007102");
        SatisRuntimeAuthority authority = new SatisRuntimeAuthority("node-b");

        authority.activated(islandId, "node-b", "island_world", 4, 5, 42L);

        assertTrue(authority.canTick(islandId));
        assertTrue(authority.canWrite(islandId));
        assertTrue(authority.canWrite(islandId, 42L));
        assertFalse(authority.canWrite(islandId, 41L));
        assertEquals(42L, authority.snapshot(islandId).fencingToken());
    }

    @Test
    void recoveryAndRemoteOwnerBlockRuntime() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000007103");
        SatisRuntimeAuthority authority = new SatisRuntimeAuthority("node-a");

        authority.activated(islandId, "node-b", "island_world", 0, 0, 7L);
        assertFalse(authority.canTick(islandId));
        assertFalse(authority.canWrite(islandId, 7L));

        authority.suspend(islandId, "RECOVERY_REQUIRED", "node-a");
        assertFalse(authority.canTick(islandId));
        assertFalse(authority.canWrite(islandId, 7L));
    }
}
