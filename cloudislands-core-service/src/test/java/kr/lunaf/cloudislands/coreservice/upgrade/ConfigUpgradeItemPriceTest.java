package kr.lunaf.cloudislands.coreservice.upgrade;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
