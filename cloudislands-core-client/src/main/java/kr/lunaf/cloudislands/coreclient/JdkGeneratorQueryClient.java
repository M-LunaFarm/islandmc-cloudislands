package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;

public final class JdkGeneratorQueryClient implements GeneratorQueryClient {
    private final JdkCoreApiClient core;

    public JdkGeneratorQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<IslandGeneratorSnapshot> generator(UUID islandId) {
        requireIsland(islandId);
        return core.postBody("/v1/islands/generator", CoreJsonPayload.object("islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkGeneratorQueryClient::generatorView);
    }

    @Override
    public CompletableFuture<List<GeneratorRuleSnapshot>> generatorRules(UUID islandId) {
        requireIsland(islandId);
        return core.postBody("/v1/islands/generator/rules", CoreJsonPayload.object("islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkGeneratorQueryClient::ruleViews);
    }

    @Override
    public CompletableFuture<List<GeneratorRuleSnapshot>> generatorRules(String generatorKey, int level) {
        return core.postBody("/v1/islands/generator/rules", CoreJsonPayload.object("generatorKey", generatorKey == null || generatorKey.isBlank() ? "default" : generatorKey, "level", Math.max(1, level)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkGeneratorQueryClient::ruleViews);
    }

    static IslandGeneratorSnapshot generatorView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> generator = CoreJson.objectValue(root, "generator");
        return new IslandGeneratorSnapshot(
            uuid(CoreJson.text(generator, "islandId")),
            CoreJson.text(generator, "generatorKey"),
            intValue(generator, "level"),
            instant(CoreJson.text(generator, "updatedAt"))
        );
    }

    static List<GeneratorRuleSnapshot> ruleViews(String body) {
        return CoreJson.entries(body, "rules").stream()
            .map(rule -> new GeneratorRuleSnapshot(
                CoreJson.text(rule, "generatorKey"),
                CoreJson.text(rule, "materialKey"),
                CoreJson.decimal(rule, "chance"),
                intValue(rule, "minIslandLevel"),
                intValue(rule, "minUpgradeLevel"),
                CoreJson.text(rule, "biomeKey"),
                CoreJson.bool(rule, "enabled", true)
            ))
            .filter(rule -> !rule.materialKey().isBlank())
            .toList();
    }

    private static int intValue(Map<?, ?> object, String key) {
        return (int) Math.max(0L, CoreJson.number(object, key));
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return new UUID(0L, 0L);
        }
    }

    private static Instant instant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
