package kr.lunaf.cloudislands.api.environment;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class IslandBiomePolicy {
    private static final List<String> SUPPORTED_BIOMES = List.of(
        "minecraft:plains",
        "minecraft:forest",
        "minecraft:cherry_grove",
        "minecraft:desert",
        "minecraft:snowy_plains",
        "minecraft:jungle",
        "minecraft:swamp",
        "minecraft:badlands",
        "minecraft:taiga",
        "minecraft:mushroom_fields"
    );

    private IslandBiomePolicy() {
    }

    public static List<String> supportedBiomes() {
        return SUPPORTED_BIOMES;
    }

    public static Optional<String> normalize(String biomeKey) {
        String normalized = biomeKey == null ? "" : biomeKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if (!normalized.contains(":")) {
            normalized = "minecraft:" + normalized;
        }
        return SUPPORTED_BIOMES.contains(normalized) ? Optional.of(normalized) : Optional.empty();
    }
}
