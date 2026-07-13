package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SoftExplosionProtectionPolicyTest {
    @Test
    void classifiesTriggerBlockWithoutTreatingDestructiveExplosionsAsSoft() {
        assertTrue(SoftExplosionProtectionPolicy.isSoft(ExplosionResult.TRIGGER_BLOCK));
        assertFalse(SoftExplosionProtectionPolicy.isSoft(ExplosionResult.DESTROY));
        assertFalse(SoftExplosionProtectionPolicy.isSoft(ExplosionResult.DESTROY_WITH_DECAY));
        assertFalse(SoftExplosionProtectionPolicy.isSoft(ExplosionResult.KEEP));
    }

    @Test
    void mapsWindChargeTargetsToGranularIslandPermissions() {
        assertEquals(IslandPermission.BREAK, SoftExplosionProtectionPolicy.requiredPermission(Material.CHORUS_FLOWER));
        assertEquals(IslandPermission.BREAK, SoftExplosionProtectionPolicy.requiredPermission(Material.POINTED_DRIPSTONE));
        assertEquals(IslandPermission.INTERACT, SoftExplosionProtectionPolicy.requiredPermission(Material.BELL));
        assertEquals(IslandPermission.USE_DOOR, SoftExplosionProtectionPolicy.requiredPermission(Material.OAK_DOOR));
        assertEquals(IslandPermission.USE_DOOR, SoftExplosionProtectionPolicy.requiredPermission(Material.OAK_TRAPDOOR));
        assertEquals(IslandPermission.USE_DOOR, SoftExplosionProtectionPolicy.requiredPermission(Material.OAK_FENCE_GATE));
        assertEquals(IslandPermission.USE_BUTTON, SoftExplosionProtectionPolicy.requiredPermission(Material.STONE_BUTTON));
        assertEquals(IslandPermission.USE_PRESSURE_PLATE, SoftExplosionProtectionPolicy.requiredPermission(Material.OAK_PRESSURE_PLATE));
        assertEquals(IslandPermission.USE_REDSTONE, SoftExplosionProtectionPolicy.requiredPermission(Material.LEVER));
        assertNull(SoftExplosionProtectionPolicy.requiredPermission(Material.STONE));
    }
}
