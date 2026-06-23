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
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class RoutePreparationRoutesTest {
    @Test
    void registersRoutePreparationEndpointGroup() {
        List<String> paths = new ArrayList<>();
        RoutePreparationRoutes routes = new RoutePreparationRoutes(null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/routes/home"));
        assertTrue(paths.contains("/v1/routes/migration-return"));
        assertTrue(paths.contains("/v1/admin/islands/tp"));
    }

    @Test
    void registersRoutePreparationEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new RoutePreparationRoutes(null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/routes/home"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/visit"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/random"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/warp"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/migration-return"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/tp"));
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
