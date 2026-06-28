package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.api.generator.IslandGeneratorSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class GeneratorRoutesTest {
    @Test
    void registersGeneratorEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        assertDoesNotThrow(() -> new GeneratorRoutes(null, null).register(registry));

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/generator"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/generator/rules"));
    }

    @Test
    void rendersGeneratorProfileAndRuleContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        IslandGeneratorSnapshot profile = new IslandGeneratorSnapshot(islandId, "nether", 3, Instant.parse("2026-01-02T03:04:05Z"));
        List<GeneratorRuleSnapshot> rules = List.of(
            new GeneratorRuleSnapshot("nether", "minecraft:basalt", 70.0D, 0, 1, "nether_wastes", true),
            new GeneratorRuleSnapshot("nether", "minecraft:quartz_ore", 30.0D, 10, 2, "nether_wastes", true)
        );

        Map<?, ?> generatorRoot = SimpleJson.object(SimpleJson.parse(GeneratorRoutes.generatorJson(profile)));
        Map<?, ?> generator = SimpleJson.object(generatorRoot.get("generator"));
        Map<?, ?> rulesRoot = SimpleJson.object(SimpleJson.parse(GeneratorRoutes.generatorRulesJson("nether", 3, rules)));
        Map<?, ?> firstRule = SimpleJson.object(SimpleJson.list(rulesRoot.get("rules")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(generator.get("islandId")));
        assertEquals("nether", SimpleJson.text(generator.get("generatorKey")));
        assertEquals(3, ((Number) generator.get("level")).intValue());
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(generator.get("updatedAt")));
        assertEquals("nether", SimpleJson.text(rulesRoot.get("generatorKey")));
        assertEquals(3, ((Number) rulesRoot.get("level")).intValue());
        assertEquals("minecraft:basalt", SimpleJson.text(firstRule.get("materialKey")));
        assertEquals(70.0D, ((Number) firstRule.get("chance")).doubleValue());
        assertEquals(0, ((Number) firstRule.get("minIslandLevel")).intValue());
        assertEquals(1, ((Number) firstRule.get("minUpgradeLevel")).intValue());
        assertEquals("nether_wastes", SimpleJson.text(firstRule.get("biomeKey")));
        assertEquals(true, firstRule.get("enabled"));
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
