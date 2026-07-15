package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigUpgradeItemPriceTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesBankAndMultipleItemPricesForOneLevel() throws Exception {
        Path config = tempDir.resolve("upgrades.yml");
        Files.writeString(config, """
            upgrades:
              size:
                type: "ISLAND_SIZE"
                levels:
                  1:
                    cost: 2500
                    item-costs:
                      DIAMOND: 4
                      minecraft:emerald: 2
                    size: 125
            """);

        UpgradeRule rule = ConfigUpgradePolicy.load(config.toString()).rule("size");

        assertEquals("2500", rule.costForNextLevel(0).toPlainString());
        assertEquals(Map.of("minecraft:diamond", 4L, "minecraft:emerald", 2L), rule.itemCostsForNextLevel(0));
        assertEquals(125L, rule.limitValueForLevel(1).orElseThrow());
    }

    @Test
    void preservesConcurrentScalarAndNestedEffectsAcrossLevels() throws Exception {
        Path config = tempDir.resolve("multi-effects.yml");
        Files.writeString(config, """
            upgrades:
              utility:
                type: "ISLAND_SIZE"
                levels:
                  1:
                    cost: 1000
                    crops-growth: 1.1
                    mob-drops: 1.75
                    spawner-rates: 0.8
                    team-limit: 4
                    size: 125
                    island-effects:
                      SPEED: 1
                    role-limits:
                      trusted: 5
                  2:
                    cost: 2000
                    crops-growth: 1.4
                    island-effects:
                      SPEED: 2
                      HASTE: 1
            """);

        UpgradeRule rule = ConfigUpgradePolicy.load(config.toString()).rule("utility");

        assertEquals(125L, rule.limitValueForLevel(1).orElseThrow(), "legacy scalar access must not be overwritten by later effects");
        assertEquals(Map.of(
            "size", 125L,
            "crops-growth", 110L,
            "mob-drops", 175L,
            "spawner-rates", 80L,
            "team-limit", 4L,
            "island-effects.speed", 1L,
            "role-limits.trusted", 5L
        ), rule.effectsForLevel(1));
        assertEquals(Map.of(
            "size", 125L,
            "crops-growth", 140L,
            "mob-drops", 175L,
            "spawner-rates", 80L,
            "team-limit", 4L,
            "island-effects.speed", 2L,
            "island-effects.haste", 1L,
            "role-limits.trusted", 5L
        ), rule.effectsForLevel(2));
    }

    @Test
    void acceptsQuotedLevelsPriceAndDeepMapsFromSs2Layout() throws Exception {
        Path config = tempDir.resolve("ss2-upgrades.yml");
        Files.writeString(config, """
            upgrades:
              island-generators:
                '1':
                  price: 100000.0
                  price-type: money
                  crops-growth: 1.25
                  generator-rates:
                    normal:
                      STONE: 85
                      COAL_ORE: 15
            """);

        UpgradeRule rule = ConfigUpgradePolicy.load(config.toString()).rule("island-generators");

        assertEquals("100000.0", rule.costForNextLevel(0).toPlainString());
        assertEquals(1, rule.maxLevel());
        assertTrue(rule.limitValueForLevel(1).isEmpty(), "a custom composite upgrade must not inherit an unrelated size effect");
        assertEquals(Map.of(
            "crops-growth", 125L,
            "generator-rates.normal.stone", 85L,
            "generator-rates.normal.coal-ore", 15L
        ), rule.effectsForLevel(1));
    }
}
