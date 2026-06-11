package kr.lunaf.cloudislands.paper.generator;

import java.util.HashMap;
import java.util.Map;

public final class GeneratorRegistry {
    private final Map<String, Map<Integer, GeneratorRule>> rules = new HashMap<>();

    public void put(String generatorKey, int level, GeneratorRule rule) {
        rules.computeIfAbsent(generatorKey, ignored -> new HashMap<>()).put(level, rule);
    }

    public GeneratorRule rule(String generatorKey, int level) {
        Map<Integer, GeneratorRule> byLevel = rules.get(generatorKey);
        if (byLevel == null || byLevel.isEmpty()) {
            GeneratorRule fallback = new GeneratorRule();
            fallback.add("minecraft:cobblestone", 1);
            return fallback;
        }
        for (int current = level; current >= 1; current--) {
            GeneratorRule rule = byLevel.get(current);
            if (rule != null) {
                return rule;
            }
        }
        return byLevel.values().iterator().next();
    }
}
