package kr.lunaf.cloudislands.paper.generator;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratorSystemPolicyTest {
    @Test
    void pinsGeneratorConfigAndEventPolicies() {
        assertEquals("generator-rules-load-from-rules-generators-yaml-with-data-folder-override", GeneratorSystemPolicy.CONFIG_SOURCE_POLICY);
        assertEquals("paper-listener-replaces-generated-blocks-on-block-form-and-fluid-collision-events", GeneratorSystemPolicy.APPLICATION_POLICY);
        assertEquals("island-generator-level-is-read-from-core-upgrades-and-cached-for-event-hot-path", GeneratorSystemPolicy.LEVEL_CACHE_POLICY);
        assertTrue(GeneratorSystemPolicy.handledEvent("BlockFormEvent"));
        assertTrue(GeneratorSystemPolicy.handledEvent("BlockFromToEvent"));
        assertTrue(GeneratorSystemPolicy.handledEvent("fluid-collision-detection"));
    }

    @Test
    void defaultGeneratorRulesKeepGoalWeightsForFirstThreeLevels() {
        GeneratorRegistry registry = DefaultGeneratorRules.create();

        assertWeights(
                registry.rule("default", 1).materialWeights(),
                Map.of("minecraft:cobblestone", 90, "minecraft:coal_ore", 10)
        );
        assertWeights(
                registry.rule("default", 2).materialWeights(),
                Map.of("minecraft:cobblestone", 75, "minecraft:coal_ore", 15, "minecraft:iron_ore", 10)
        );
        assertWeights(
                registry.rule("default", 3).materialWeights(),
                Map.of("minecraft:cobblestone", 60, "minecraft:coal_ore", 15, "minecraft:iron_ore", 15, "minecraft:diamond_ore", 10)
        );
    }

    @Test
    void generatorRegistryFallsBackToClosestAvailableLevelAndCobblestoneDefault() {
        GeneratorRegistry registry = DefaultGeneratorRules.create();

        assertEquals(registry.rule("default", 5).materialWeights(), registry.rule("default", 99).materialWeights());
        assertEquals("minecraft:cobblestone", new GeneratorRegistry().rule("missing", 7).select(new Random(1L)));
    }

    @Test
    void generatorRuleIgnoresInvalidWeightsAndTracksTotalWeight() {
        GeneratorRule rule = new GeneratorRule();
        rule.add("minecraft:cobblestone", 90);
        rule.add("minecraft:coal_ore", 10);
        rule.add("minecraft:diamond_ore", 0);
        rule.add("minecraft:emerald_ore", -1);

        assertEquals(100, rule.totalWeight());
        assertEquals(2, rule.materialWeights().size());
        assertTrue(rule.materialWeights().containsKey("minecraft:cobblestone"));
        assertTrue(rule.materialWeights().containsKey("minecraft:coal_ore"));
    }

    private void assertWeights(Map<String, Integer> actual, Map<String, Integer> expected) {
        assertEquals(expected, actual);
        assertEquals(100, actual.values().stream().mapToInt(Integer::intValue).sum());
    }
}
