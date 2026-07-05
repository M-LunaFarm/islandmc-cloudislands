package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public interface GeneratorCommandClient {
    CompletableFuture<IslandGeneratorSnapshot> adminSetGenerator(UUID islandId, String generatorKey, int level);

    CompletableFuture<IslandGeneratorSnapshot> adminAddGeneratorLevels(UUID islandId, String generatorKey, int levels);

    CompletableFuture<IslandGeneratorSnapshot> adminClearGenerator(UUID islandId);
}
