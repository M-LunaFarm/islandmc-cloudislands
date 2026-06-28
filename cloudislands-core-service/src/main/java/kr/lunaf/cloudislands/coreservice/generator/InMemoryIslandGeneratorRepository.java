package kr.lunaf.cloudislands.coreservice.generator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public final class InMemoryIslandGeneratorRepository implements IslandGeneratorRepository {
    private final Map<UUID, IslandGeneratorSnapshot> profiles = new ConcurrentHashMap<>();
    private final Map<String, List<GeneratorRuleSnapshot>> rules = new ConcurrentHashMap<>();

    public InMemoryIslandGeneratorRepository() {
        for (GeneratorRuleSnapshot rule : DefaultGeneratorRules.all()) {
            rules.computeIfAbsent(rule.generatorKey(), ignored -> new ArrayList<>()).add(rule);
        }
    }

    @Override
    public IslandGeneratorSnapshot profile(UUID islandId) {
        return profiles.getOrDefault(islandId, new IslandGeneratorSnapshot(islandId, "default", 1, Instant.EPOCH));
    }

    @Override
    public IslandGeneratorSnapshot setProfile(UUID islandId, String generatorKey, int level) {
        IslandGeneratorSnapshot snapshot = new IslandGeneratorSnapshot(islandId, generatorKey, level, Instant.now());
        profiles.put(islandId, snapshot);
        return snapshot;
    }

    @Override
    public List<GeneratorRuleSnapshot> rules(String generatorKey) {
        String key = safeGeneratorKey(generatorKey);
        List<GeneratorRuleSnapshot> result = rules.getOrDefault(key, List.of());
        return result.isEmpty() && !key.equals("default") ? rules("default") : List.copyOf(result);
    }

    @Override
    public List<GeneratorRuleSnapshot> setRules(String generatorKey, List<GeneratorRuleSnapshot> nextRules) {
        String key = safeGeneratorKey(generatorKey);
        List<GeneratorRuleSnapshot> normalized = (nextRules == null ? List.<GeneratorRuleSnapshot>of() : nextRules).stream()
            .filter(GeneratorRuleSnapshot::enabled)
            .map(rule -> new GeneratorRuleSnapshot(key, rule.materialKey(), rule.chance(), rule.minIslandLevel(), rule.minUpgradeLevel(), rule.biomeKey(), rule.enabled()))
            .toList();
        rules.put(key, normalized);
        return List.copyOf(normalized);
    }

    private static String safeGeneratorKey(String generatorKey) {
        return generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey.trim().toLowerCase();
    }
}
