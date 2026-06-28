package kr.lunaf.cloudislands.api.generator;

public record GeneratorRuleSnapshot(
    String generatorKey,
    String materialKey,
    double chance,
    int minIslandLevel,
    int minUpgradeLevel,
    String biomeKey,
    boolean enabled
) {
    public GeneratorRuleSnapshot {
        generatorKey = safe(generatorKey, "default").toLowerCase();
        materialKey = safe(materialKey, "minecraft:cobblestone").toLowerCase();
        chance = Math.max(0.0D, chance);
        minIslandLevel = Math.max(0, minIslandLevel);
        minUpgradeLevel = Math.max(0, minUpgradeLevel);
        biomeKey = safe(biomeKey, "*").toLowerCase();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
