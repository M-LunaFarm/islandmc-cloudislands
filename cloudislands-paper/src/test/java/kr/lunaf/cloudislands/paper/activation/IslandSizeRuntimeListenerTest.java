package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.junit.jupiter.api.Test;

class IslandSizeRuntimeListenerTest {
    @Test
    void validSizeMutationAtomicallyReplacesActiveProtectionBounds() {
        UUID islandId = UUID.randomUUID();
        ActiveIslandRegistry active = activeIsland(islandId, 300);
        ProtectionController protection = protection(islandId, 300);
        IslandSizeRuntimeListener listener = new IslandSizeRuntimeListener(
            active,
            new ShardWorldManager("ci_shard_", 1, 1024),
            protection,
            Logger.getLogger("test")
        );

        IslandSizeRuntimeListener.SyncResult result = listener.synchronize(islandId, 400L);

        assertEquals(IslandSizeRuntimeListener.SyncResult.APPLIED, result);
        assertEquals(400, active.find(islandId).orElseThrow().islandSize());
        IslandRegion region = protection.region(islandId).orElseThrow();
        assertEquals(-200, region.minX());
        assertEquals(200, region.maxX());
        assertFalse(protection.isMigrating(islandId));
    }

    @Test
    void unsafeSizeMutationFencesIslandAndPreservesLastSafeBounds() {
        UUID islandId = UUID.randomUUID();
        ActiveIslandRegistry active = activeIsland(islandId, 300);
        ProtectionController protection = protection(islandId, 300);
        IslandSizeRuntimeListener listener = new IslandSizeRuntimeListener(
            active,
            new ShardWorldManager("ci_shard_", 1, 1024),
            protection,
            Logger.getLogger("test")
        );

        IslandSizeRuntimeListener.SyncResult result = listener.synchronize(islandId, 1024L);

        assertEquals(IslandSizeRuntimeListener.SyncResult.FENCED_UNSAFE_SIZE, result);
        assertEquals(300, active.find(islandId).orElseThrow().islandSize());
        assertEquals(150, protection.region(islandId).orElseThrow().maxX());
        assertTrue(protection.isMigrating(islandId));
    }

    private static ActiveIslandRegistry activeIsland(UUID islandId, int size) {
        ActiveIslandRegistry registry = new ActiveIslandRegistry();
        registry.activated(new IslandActivationJobHandler.ActivationResult(
            true, "ACTIVE", islandId, "ci_shard_001", 0, 0, 0, 0, size, 3L, 11L,
            null, 0L, "", 0L, "", 0L, "", 0L, "core-payload"
        ));
        return registry;
    }

    private static ProtectionController protection(UUID islandId, int size) {
        ProtectionController protection = new ProtectionController(new RegionIndex(), new LocalIslandPermissionCache());
        protection.registerIsland(islandId, "ci_shard_001", 0, 0, size, 0, 0);
        return protection;
    }
}
