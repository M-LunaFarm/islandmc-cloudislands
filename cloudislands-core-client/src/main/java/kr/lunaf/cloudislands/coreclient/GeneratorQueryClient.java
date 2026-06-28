package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public interface GeneratorQueryClient {
    CompletableFuture<IslandGeneratorSnapshot> generator(UUID islandId);
    CompletableFuture<List<GeneratorRuleSnapshot>> generatorRules(UUID islandId);
    CompletableFuture<List<GeneratorRuleSnapshot>> generatorRules(String generatorKey, int level);
}
