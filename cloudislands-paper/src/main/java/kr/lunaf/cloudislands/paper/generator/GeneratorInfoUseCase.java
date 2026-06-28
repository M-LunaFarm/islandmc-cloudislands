package kr.lunaf.cloudislands.paper.generator;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
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
        CompletableFuture<kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot> profile = client.generators().generator(islandId);
        CompletableFuture<List<GeneratorRuleSnapshot>> rules = client.generators().generatorRules(islandId);
        return profile.thenCombine(rules, (generator, generatorRules) -> new GeneratorInfoView(
            generator.generatorKey().isBlank() ? defaultGeneratorKey : generator.generatorKey(),
            generator.level(),
            sortedMaterials(generatorRules),
            totalChance(generatorRules)
        ));
    }

    private static List<GeneratorMaterialView> sortedMaterials(List<GeneratorRuleSnapshot> rules) {
        return (rules == null ? List.<GeneratorRuleSnapshot>of() : rules).stream()
            .filter(GeneratorRuleSnapshot::enabled)
            .sorted(Comparator.comparing(GeneratorRuleSnapshot::chance).reversed().thenComparing(GeneratorRuleSnapshot::materialKey))
            .map(rule -> new GeneratorMaterialView(rule.materialKey(), (int) Math.round(rule.chance())))
            .toList();
    }

    private static int totalChance(List<GeneratorRuleSnapshot> rules) {
        return (int) Math.round((rules == null ? List.<GeneratorRuleSnapshot>of() : rules).stream()
            .filter(GeneratorRuleSnapshot::enabled)
            .mapToDouble(GeneratorRuleSnapshot::chance)
            .sum());
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
