package kr.lunaf.cloudislands.paper.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.GeneratorQueryClient;
import org.junit.jupiter.api.Test;

class GeneratorInfoUseCaseTest {
    @Test
    void generatorInfoUsesDedicatedRulesAndResolvedGeneratorProfile() {
        GeneratorRegistry registry = new GeneratorRegistry();
        GeneratorRule rule = new GeneratorRule();
        rule.add("minecraft:basalt", 80);
        rule.add("minecraft:blackstone", 20);
        registry.put("nether", 2, rule);

        GeneratorInfoUseCase.GeneratorInfoView view = new GeneratorInfoUseCase(client(), registry)
            .view(UUID.fromString("00000000-0000-0000-0000-000000000501"))
            .join();

        assertEquals("nether", view.generatorKey());
        assertEquals(2, view.level());
        assertEquals(100, view.totalWeight());
        assertEquals("minecraft:basalt", view.materials().getFirst().materialKey());
        assertEquals(80, view.materials().getFirst().weight());
    }

    @Test
    void generatorProfileFallsBackToUpgradeKeySuffixWhenCoreOmitsGeneratorKey() {
        GeneratorLevelCache.GeneratorProfile profile = GeneratorLevelCache.resolveProfile(
            List.of(new CoreGuiViews.UpgradeView("generator:ore", "GENERATOR", 3, "")),
            "default"
        );

        assertEquals("ore", profile.generatorKey());
        assertEquals(3, profile.level());
    }

    private static CoreApiClient client() {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class, GeneratorQueryClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "generators" -> (GeneratorQueryClient) _proxy;
                case "generator" -> CompletableFuture.completedFuture(new IslandGeneratorSnapshot((UUID) args[0], "nether", 2, null));
                case "generatorRules" -> CompletableFuture.completedFuture(List.of(
                    new GeneratorRuleSnapshot("nether", "minecraft:basalt", 80.0D, 0, 1, "*", true),
                    new GeneratorRuleSnapshot("nether", "minecraft:blackstone", 20.0D, 0, 1, "*", true)
                ));
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }
}
