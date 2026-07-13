package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class IslandBlockLimitKeysTest {
    @Test
    void mapsPhysicalMaterialsToStableInternalCountKeys() {
        assertEquals("HOPPER", IslandBlockLimitKeys.limitKey(Material.HOPPER));
        assertEquals("cloudislands:limit/hopper", IslandBlockLimitKeys.countKey(Material.HOPPER));
        assertEquals("cloudislands:limit/spawner", IslandBlockLimitKeys.countKey(Material.SPAWNER));
        assertEquals("cloudislands:limit/redstone", IslandBlockLimitKeys.countKey(Material.OAK_BUTTON));
        assertEquals("cloudislands:limit/redstone", IslandBlockLimitKeys.countKey(Material.POWERED_RAIL));
        assertNull(IslandBlockLimitKeys.countKey(Material.STONE));
    }
}
