package kr.lunaf.cloudislands.paper.limit;

import org.bukkit.Material;

public final class IslandBlockLimitKeys {
    public static final String COUNT_PREFIX = "cloudislands:limit/";

    private IslandBlockLimitKeys() {
    }

    public static String limitKey(Material material) {
        if (material == Material.HOPPER) {
            return "HOPPER";
        }
        if (material == Material.SPAWNER) {
            return "SPAWNER";
        }
        if (isRedstone(material)) {
            return "REDSTONE";
        }
        return null;
    }

    public static String countKey(Material material) {
        String limitKey = limitKey(material);
        return limitKey == null ? null : COUNT_PREFIX + limitKey.toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean isRedstone(Material material) {
        if (material == null) {
            return false;
        }
        String key = material.getKey().getKey();
        return key.contains("redstone")
            || key.endsWith("_button")
            || key.endsWith("_pressure_plate")
            || key.endsWith("_piston")
            || key.endsWith("_rail")
            || material == Material.REPEATER
            || material == Material.COMPARATOR
            || material == Material.LEVER
            || material == Material.OBSERVER
            || material == Material.DISPENSER
            || material == Material.DROPPER
            || material == Material.DAYLIGHT_DETECTOR
            || material == Material.TRIPWIRE_HOOK
            || material == Material.TRAPPED_CHEST
            || material == Material.TARGET
            || material == Material.NOTE_BLOCK;
    }
}
