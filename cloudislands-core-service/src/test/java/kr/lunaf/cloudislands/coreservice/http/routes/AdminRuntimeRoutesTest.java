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
import org.junit.jupiter.api.Test;

class AdminRuntimeRoutesTest {
    @Test
    void registersAdminRuntimeEndpointGroup() {
        List<String> paths = new ArrayList<>();
        AdminRuntimeRoutes routes = new AdminRuntimeRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/admin/cache/clear"));
        assertTrue(paths.contains("/v1/admin/reload"));
    }

    @Test
    void registersAdminRuntimeMutationsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new AdminRuntimeRoutes(null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/cache/clear"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/reload"));
    }

    @Test
    void rendersCacheClearAndReloadResponses() {
        AdminRuntimeRoutes.ClearResult result = new AdminRuntimeRoutes.ClearResult(2, 3, 4);

        Map<?, ?> cleared = SimpleJson.object(SimpleJson.parse(result.json(false)));
        Map<?, ?> reloaded = SimpleJson.object(SimpleJson.parse(result.json(true)));

        assertEquals(2, ((Number) cleared.get("clearedSessions")).intValue());
        assertEquals(3, ((Number) cleared.get("clearedTickets")).intValue());
        assertEquals(4, ((Number) cleared.get("clearedRedisKeys")).intValue());
        assertEquals(true, reloaded.get("reloaded"));
        assertEquals(2, ((Number) reloaded.get("clearedSessions")).intValue());
        assertEquals(3, ((Number) reloaded.get("clearedTickets")).intValue());
        assertEquals(4, ((Number) reloaded.get("clearedRedisKeys")).intValue());
        assertEquals("application-cache", result.cacheClearEventFields().get("scope"));
        assertEquals("2", result.reloadEventFields().get("clearedSessions"));
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
