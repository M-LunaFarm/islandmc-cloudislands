package kr.lunaf.cloudislands.paper.generator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;

public final class GeneratorRule {
    private final NavigableMap<Integer, String> weightedMaterials = new TreeMap<>();
    private final Map<String, Integer> configuredWeights = new LinkedHashMap<>();
    private int totalWeight;

    public void add(String materialKey, int weight) {
        if (weight <= 0) {
            return;
        }
        totalWeight += weight;
        weightedMaterials.put(totalWeight, materialKey);
        configuredWeights.merge(materialKey, weight, Integer::sum);
    }

    public String select(Random random) {
        if (totalWeight <= 0) {
            return "minecraft:cobblestone";
        }
        int value = random.nextInt(totalWeight) + 1;
        return weightedMaterials.ceilingEntry(value).getValue();
    }

    public Map<String, Integer> materialWeights() {
        return Map.copyOf(configuredWeights);
    }

    public int totalWeight() {
        return totalWeight;
    }
}
