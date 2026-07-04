package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;
import org.junit.jupiter.api.Test;

class TemplateRoutesTest {
    @Test
    void registersTemplateEndpointGroup() {
        List<String> paths = new ArrayList<>();
        TemplateRoutes routes = new TemplateRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(9, paths.size());
        assertTrue(paths.contains("/v1/admin/templates/list"));
        assertTrue(paths.contains("/v1/admin/templates/get"));
        assertTrue(paths.contains("/v1/admin/templates/upsert"));
        assertTrue(paths.contains("/v1/admin/templates/import-bundle"));
        assertTrue(paths.contains("/v1/admin/templates/verify-bundle"));
        assertTrue(paths.contains("/v1/admin/templates/enable"));
        assertTrue(paths.contains("/v1/admin/templates/disable"));
        assertTrue(paths.contains("/v1/admin/templates/delete"));
        assertTrue(paths.contains("/v1/admin/templates/reorder"));
    }

    @Test
    void registersTemplateEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new TemplateRoutes(null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/list"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/get"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/upsert"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/import-bundle"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/verify-bundle"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/enable"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/disable"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/delete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/templates/reorder"));
    }

    @Test
    void rendersTemplateList() {
        String json = TemplateRoutes.templatesJson(List.of(
            new IslandTemplateSnapshot("default", "Default Island", true, ""),
            new IslandTemplateSnapshot("sky", "Sky \"Island\", North", "starter template", "basic", false, "1.2", "cloudislands.template.sky", "GRASS_BLOCK", 7, "preview/sky.png", "templates/sky.tar.zst", "abc123", 4096L, 3, 256, 4.5D, 101.0D, -3.5D, 90.0F, 5.0F, "spawn", "void", "minecraft:plains", "CYAN", "25", "100", 3, List.of("starter", "premium"), java.time.Instant.EPOCH, java.time.Instant.EPOCH)
        ));
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
        List<?> templates = SimpleJson.list(root.get("templates"));
        Map<?, ?> first = SimpleJson.object(templates.get(0));
        Map<?, ?> second = SimpleJson.object(templates.get(1));

        assertEquals("default", SimpleJson.text(first.get("id")));
        assertEquals(true, first.get("enabled"));
        assertEquals("Sky \"Island\", North", SimpleJson.text(second.get("displayName")));
        assertEquals("1.2", SimpleJson.text(second.get("minNodeVersion")));
        assertEquals("templates/sky.tar.zst", SimpleJson.text(second.get("bundleStoragePath")));
        assertEquals("abc123", SimpleJson.text(second.get("bundleChecksum")));
        assertEquals("spawn", SimpleJson.text(second.get("homeName")));
        assertEquals("100", SimpleJson.text(second.get("creationCost")));
    }

    @Test
    void protectsMigrationInputOnlyTemplate() {
        assertTrue(TemplateRoutes.migrationInputOnlyTemplate("superiorskyblock2"));
        assertTrue(TemplateRoutes.migrationInputOnlyTemplate(" SuperiorSkyblock2 "));
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
