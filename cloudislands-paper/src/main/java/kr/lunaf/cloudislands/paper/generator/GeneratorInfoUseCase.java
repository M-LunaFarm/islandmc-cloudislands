package kr.lunaf.cloudislands.paper.generator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class GeneratorInfoUseCase {
    private final CoreApiClient client;
    private final GeneratorRegistry registry;
    private final String defaultGeneratorKey;

    public GeneratorInfoUseCase(CoreApiClient client, GeneratorRegistry registry) {
        this(client, registry, "default");
    }

    public GeneratorInfoUseCase(CoreApiClient client, GeneratorRegistry registry, String defaultGeneratorKey) {
        if (client == null) {
            throw new IllegalArgumentException("client is required");
        }
        this.client = client;
        this.registry = registry == null ? DefaultGeneratorRules.create() : registry;
        this.defaultGeneratorKey = defaultGeneratorKey == null || defaultGeneratorKey.isBlank() ? "default" : defaultGeneratorKey.trim().toLowerCase(java.util.Locale.ROOT);
    }

    public CompletableFuture<GeneratorInfoView> view(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return client.progression().upgrades(islandId)
            .thenApply(upgrades -> {
                GeneratorLevelCache.GeneratorProfile profile = GeneratorLevelCache.resolveProfile(upgrades, defaultGeneratorKey);
                GeneratorRule rule = registry.rule(profile.generatorKey(), profile.level());
                return new GeneratorInfoView(profile.generatorKey(), profile.level(), sortedMaterials(rule.materialWeights()), rule.totalWeight());
            });
    }

    private static List<GeneratorMaterialView> sortedMaterials(Map<String, Integer> weights) {
        return (weights == null ? Map.<String, Integer>of() : weights).entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry<String, Integer>::getValue).reversed().thenComparing(Map.Entry::getKey))
            .map(entry -> new GeneratorMaterialView(entry.getKey(), entry.getValue()))
            .toList();
    }

    public record GeneratorInfoView(String generatorKey, int level, List<GeneratorMaterialView> materials, int totalWeight) {
        public GeneratorInfoView {
            generatorKey = generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey;
            level = Math.max(1, level);
            materials = materials == null ? List.of() : List.copyOf(materials);
            totalWeight = Math.max(0, totalWeight);
        }
    }

    public record GeneratorMaterialView(String materialKey, int weight) {
        public GeneratorMaterialView {
            materialKey = materialKey == null ? "" : materialKey;
            weight = Math.max(0, weight);
        }
    }
}
