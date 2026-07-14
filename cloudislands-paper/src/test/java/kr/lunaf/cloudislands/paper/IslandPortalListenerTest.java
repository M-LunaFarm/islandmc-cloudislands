package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import org.junit.jupiter.api.Test;

class IslandPortalListenerTest {
    @Test
    void blocksOnlyLocationsInsideActiveIslandRegions() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        ProtectionController protection = new ProtectionController(new RegionIndex(), new LocalIslandPermissionCache());
        protection.registerIsland(islandId, "ci_shard_001", 0, 0, 300, 0, 0);
        IslandPortalListener listener = new IslandPortalListener(protection, null);

        assertTrue(listener.blocks("ci_shard_001", 0, 0));
        assertTrue(listener.blocks("ci_shard_001", 150, 150));
        assertFalse(listener.blocks("ci_shard_001", 151, 0));
        assertFalse(listener.blocks("world", 0, 0));
        assertEquals(0L, listener.blockedPlayerPortals());
        assertEquals(0L, listener.blockedEntityPortals());
        assertEquals(IslandPortalListener.POLICY, listener.policy());

        protection.unregisterIsland(islandId);
        assertFalse(listener.blocks("ci_shard_001", 0, 0));
    }

    @Test
    void playerAndEntityPortalEventsFailClosedWithoutCreatingDestinations() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandPortalListener.java"));

        assertTrue(source.contains("onPlayerPortal(PlayerPortalEvent event)"));
        assertTrue(source.contains("onEntityPortal(EntityPortalEvent event)"));
        assertTrue(source.contains("event.setCanCreatePortal(false)"));
        assertTrue(source.contains("event.setCancelled(true)"));
        assertTrue(source.contains("setPortalCooldown(PORTAL_RETRY_COOLDOWN_TICKS)"));
        assertTrue(source.contains("PlayerLocaleCache.clientLocale(player)"));
        assertTrue(source.contains("portal-cross-dimension-denied"));
    }
}
