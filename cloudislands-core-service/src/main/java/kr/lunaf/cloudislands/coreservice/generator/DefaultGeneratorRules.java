package kr.lunaf.cloudislands.coreservice.generator;

import java.util.List;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;

public final class DefaultGeneratorRules {
    private DefaultGeneratorRules() {}

    public static List<GeneratorRuleSnapshot> all() {
        return List.of(
            new GeneratorRuleSnapshot("default", "minecraft:cobblestone", 70.0D, 0, 1, "*", true),
            new GeneratorRuleSnapshot("default", "minecraft:coal_ore", 20.0D, 5, 2, "*", true),
            new GeneratorRuleSnapshot("default", "minecraft:iron_ore", 8.0D, 10, 3, "*", true),
            new GeneratorRuleSnapshot("default", "minecraft:diamond_ore", 2.0D, 25, 5, "*", true),
            new GeneratorRuleSnapshot("nether", "minecraft:basalt", 60.0D, 0, 1, "nether_wastes", true),
            new GeneratorRuleSnapshot("nether", "minecraft:quartz_ore", 30.0D, 10, 2, "nether_wastes", true),
            new GeneratorRuleSnapshot("nether", "minecraft:ancient_debris", 1.0D, 35, 5, "nether_wastes", true)
        );
    }
}
