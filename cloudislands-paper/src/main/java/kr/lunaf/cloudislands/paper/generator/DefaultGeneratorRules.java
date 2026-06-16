package kr.lunaf.cloudislands.paper.generator;

public final class DefaultGeneratorRules {
    private DefaultGeneratorRules() {}

    public static GeneratorRegistry create() {
        GeneratorRegistry registry = new GeneratorRegistry();
        GeneratorRule levelOne = new GeneratorRule();
        levelOne.add("minecraft:cobblestone", 90);
        levelOne.add("minecraft:coal_ore", 10);
        registry.put("default", 1, levelOne);

        GeneratorRule levelTwo = new GeneratorRule();
        levelTwo.add("minecraft:cobblestone", 75);
        levelTwo.add("minecraft:coal_ore", 15);
        levelTwo.add("minecraft:iron_ore", 10);
        registry.put("default", 2, levelTwo);

        GeneratorRule levelThree = new GeneratorRule();
        levelThree.add("minecraft:cobblestone", 60);
        levelThree.add("minecraft:coal_ore", 15);
        levelThree.add("minecraft:iron_ore", 15);
        levelThree.add("minecraft:diamond_ore", 10);
        registry.put("default", 3, levelThree);

        GeneratorRule levelFour = new GeneratorRule();
        levelFour.add("minecraft:cobblestone", 45);
        levelFour.add("minecraft:coal_ore", 15);
        levelFour.add("minecraft:iron_ore", 18);
        levelFour.add("minecraft:gold_ore", 12);
        levelFour.add("minecraft:diamond_ore", 10);
        registry.put("default", 4, levelFour);

        GeneratorRule levelFive = new GeneratorRule();
        levelFive.add("minecraft:cobblestone", 35);
        levelFive.add("minecraft:iron_ore", 20);
        levelFive.add("minecraft:gold_ore", 15);
        levelFive.add("minecraft:diamond_ore", 15);
        levelFive.add("minecraft:emerald_ore", 15);
        registry.put("default", 5, levelFive);

        GeneratorRule netherOne = new GeneratorRule();
        netherOne.add("minecraft:basalt", 80);
        netherOne.add("minecraft:blackstone", 20);
        registry.put("nether", 1, netherOne);

        GeneratorRule netherTwo = new GeneratorRule();
        netherTwo.add("minecraft:basalt", 60);
        netherTwo.add("minecraft:blackstone", 30);
        netherTwo.add("minecraft:nether_quartz_ore", 10);
        registry.put("nether", 2, netherTwo);

        GeneratorRule netherThree = new GeneratorRule();
        netherThree.add("minecraft:basalt", 45);
        netherThree.add("minecraft:blackstone", 30);
        netherThree.add("minecraft:nether_quartz_ore", 15);
        netherThree.add("minecraft:ancient_debris", 10);
        registry.put("nether", 3, netherThree);
        return registry;
    }
}
