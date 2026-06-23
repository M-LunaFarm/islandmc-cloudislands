package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.junit.jupiter.api.Test;

class PlayerProfileRoutesTest {
    @Test
    void registersPlayerProfileEndpointGroup() {
        List<String> paths = new ArrayList<>();
        PlayerProfileRoutes routes = new PlayerProfileRoutes(null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/admin/players/info"));
        assertTrue(paths.contains("/v1/players/info"));
        assertTrue(paths.contains("/v1/players/touch"));
        assertTrue(paths.contains("/v1/players/locale"));
        assertTrue(paths.contains("/v1/admin/players/setisland"));
        assertTrue(paths.contains("/v1/admin/players/clearisland"));
    }

    @Test
    void registersPlayerProfileEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new PlayerProfileRoutes(null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/touch"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/locale"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/setisland"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/clearisland"));
    }

    @Test
    void playerProfileJsonIncludesLocale() {
        String json = PlayerProfileRoutes.playerProfileJson(new kr.lunaf.cloudislands.api.model.PlayerIslandProfile(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Steve, \"Builder\"",
            java.util.Optional.empty(),
            java.time.Instant.EPOCH,
            "EN-US"
        ));
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));

        assertEquals("Steve, \"Builder\"", SimpleJson.text(root.get("lastName")));
        assertNull(root.get("primaryIslandId"));
        assertEquals("en_us", SimpleJson.text(root.get("locale")));
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
