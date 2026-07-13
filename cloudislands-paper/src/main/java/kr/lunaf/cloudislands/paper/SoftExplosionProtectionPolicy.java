package kr.lunaf.cloudislands.paper;

import kr.lunaf.cloudislands.api.model.IslandPermission;
import org.bukkit.ExplosionResult;
import org.bukkit.Material;

/**
 * Classifies Paper explosions that update interactive blocks without destroying them.
 */
final class SoftExplosionProtectionPolicy {
    private SoftExplosionProtectionPolicy() {
    }

    static boolean isSoft(ExplosionResult result) {
        return result == ExplosionResult.TRIGGER_BLOCK;
    }

    static IslandPermission requiredPermission(Material material) {
        if (material == Material.CHORUS_FLOWER || material == Material.POINTED_DRIPSTONE) {
            return IslandPermission.BREAK;
        }
        if (material == Material.BELL) {
            return IslandPermission.INTERACT;
        }

        String key = material.getKey().getKey();
        if (key.endsWith("_door") || key.endsWith("_trapdoor") || key.endsWith("_fence_gate")) {
            return IslandPermission.USE_DOOR;
        }
        if (key.endsWith("_button")) {
            return IslandPermission.USE_BUTTON;
        }
        if (key.endsWith("_pressure_plate")) {
            return IslandPermission.USE_PRESSURE_PLATE;
        }
        if (key.equals("lever")) {
            return IslandPermission.USE_REDSTONE;
        }
        return null;
    }
}
