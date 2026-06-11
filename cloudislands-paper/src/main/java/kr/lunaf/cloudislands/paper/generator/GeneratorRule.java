package kr.lunaf.cloudislands.paper.generator;

import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public final class GeneratorRule {
    private final NavigableMap<Integer, String> weightedMaterials = new TreeMap<>();
    private int totalWeight;

    public void add(String materialKey, int weight) {
        if (weight <= 0) {
            return;
        }
        totalWeight += weight;
        weightedMaterials.put(totalWeight, materialKey);
    }

    public String select(Random random) {
        if (totalWeight <= 0) {
            return "minecraft:cobblestone";
        }
        int value = random.nextInt(totalWeight) + 1;
        return weightedMaterials.ceilingEntry(value).getValue();
    }
}
