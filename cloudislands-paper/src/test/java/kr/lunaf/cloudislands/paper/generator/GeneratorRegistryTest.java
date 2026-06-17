package kr.lunaf.cloudislands.paper.generator;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratorRegistryTest {
    @Test
    void defaultRulesMatchConfiguredGoalWeightsForFirstThreeLevels() {
        GeneratorRegistry registry = DefaultGeneratorRules.create();

        assertEquals(5, registry.ruleLevelCount() - 3);
        assertEquals("minecraft:coal_ore", registry.rule("default", 1).select(new FixedRandom(89)));
        assertEquals("minecraft:cobblestone", registry.rule("default", 2).select(new FixedRandom(74)));
        assertEquals("minecraft:coal_ore", registry.rule("default", 2).select(new FixedRandom(75)));
        assertEquals("minecraft:iron_ore", registry.rule("default", 2).select(new FixedRandom(90)));
        assertEquals("minecraft:diamond_ore", registry.rule("default", 3).select(new FixedRandom(95)));
    }

    @Test
    void missingLevelFallsBackToHighestAvailableLowerLevel() {
        GeneratorRegistry registry = new GeneratorRegistry();
        GeneratorRule levelOne = new GeneratorRule();
        levelOne.add("minecraft:cobblestone", 100);
        GeneratorRule levelThree = new GeneratorRule();
        levelThree.add("minecraft:diamond_ore", 100);
        registry.put("default", 1, levelOne);
        registry.put("default", 3, levelThree);

        assertEquals("minecraft:cobblestone", registry.rule("default", 2).select(new FixedRandom(0)));
        assertEquals("minecraft:diamond_ore", registry.rule("default", 4).select(new FixedRandom(0)));
    }

    @Test
    void emptyOrInvalidRulesFallBackToCobblestone() {
        GeneratorRule empty = new GeneratorRule();
        empty.add("minecraft:diamond_ore", 0);
        GeneratorRegistry registry = new GeneratorRegistry();

        assertEquals("minecraft:cobblestone", empty.select(new FixedRandom(0)));
        assertEquals("minecraft:cobblestone", registry.rule("missing", 5).select(new FixedRandom(0)));
    }

    private static final class FixedRandom extends Random {
        private final int value;

        private FixedRandom(int value) {
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return Math.floorMod(value, bound);
        }
    }
}
