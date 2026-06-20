package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record IslandWarehouseItemSnapshot(UUID islandId, String materialKey, long amount, Instant updatedAt) {
    public IslandWarehouseItemSnapshot {
        materialKey = normalizeMaterialKey(materialKey);
        amount = Math.max(0L, amount);
    }

    public static String normalizeMaterialKey(String value) {
        if (value == null || value.isBlank()) {
            return "minecraft:air";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return normalized.length() > 96 ? normalized.substring(0, 96) : normalized;
    }
}
