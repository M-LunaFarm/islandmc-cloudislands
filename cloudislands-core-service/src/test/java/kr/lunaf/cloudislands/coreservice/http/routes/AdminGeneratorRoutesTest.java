package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.api.generator.GeneratorRuleSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpException;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class AdminGeneratorRoutesTest {
    @Test
    void registersAdminGeneratorEndpointGroup() {
        List<String> paths = new ArrayList<>();
        AdminGeneratorRoutes routes = new AdminGeneratorRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/admin/generators/reload"));
        assertTrue(paths.contains("/v1/admin/generators/set"));
    }

    @Test
    void registersAdminGeneratorMutationsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new AdminGeneratorRoutes(null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/generators/reload"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/generators/set"));
    }

    @Test
    void parsesRulesAndValidatesChanceSum() {
        String body = """
            {
              "generatorKey": "default",
              "rules": [
                {"materialKey": "minecraft:stone", "chance": 80.0, "minIslandLevel": 0, "minUpgradeLevel": 1, "biomeKey": "*", "enabled": true},
                {"materialKey": "minecraft:coal_ore", "chance": 20.0, "minIslandLevel": 5, "minUpgradeLevel": 2, "enabled": true}
              ]
            }
            """;

        List<GeneratorRuleSnapshot> rules = AdminGeneratorRoutes.rules(body, "default");

        assertEquals(2, rules.size());
        assertEquals("minecraft:stone", rules.get(0).materialKey());
        assertEquals("*", rules.get(1).biomeKey());
        assertDoesNotThrow(() -> AdminGeneratorRoutes.validateChanceSum(rules));
    }

    @Test
    void rejectsInvalidChanceSum() {
        List<GeneratorRuleSnapshot> rules = List.of(
            new GeneratorRuleSnapshot("default", "minecraft:stone", 80.0D, 0, 1, "*", true),
            new GeneratorRuleSnapshot("default", "minecraft:coal_ore", 30.0D, 0, 1, "*", true)
        );

        assertThrows(CoreHttpException.class, () -> AdminGeneratorRoutes.validateChanceSum(rules));
    }

    @Test
    void rendersSetResponseWithDedicatedGeneratorView() {
        String json = AdminGeneratorRoutes.setJson("Default", List.of(new GeneratorRuleSnapshot("default", "minecraft:stone", 100.0D, 0, 1, "*", true)));
        Map<?, ?> values = SimpleJson.object(SimpleJson.parse(json));

        assertEquals("default", values.get("generatorKey"));
        assertEquals(1, ((Number) values.get("ruleCount")).intValue());
        assertEquals(1, SimpleJson.list(values.get("rules")).size());
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
