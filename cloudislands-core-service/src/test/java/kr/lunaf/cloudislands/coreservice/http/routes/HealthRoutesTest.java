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
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class HealthRoutesTest {
    @Test
    void registersHealthEndpointGroup() {
        List<String> paths = new ArrayList<>();
        HealthRoutes routes = new HealthRoutes(CoreServiceConfig.fromEnvironment(), () -> "");

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(List.of("/live", "/ready", "/health", "/metrics"), paths);
    }

    @Test
    void registersProbeEndpointsWithExplicitMethods() {
        RecordingRegistry registry = new RecordingRegistry();

        new HealthRoutes(CoreServiceConfig.fromEnvironment(), () -> "").register(registry);

        assertEquals(Set.of("GET"), registry.methods("/live"));
        assertEquals(Set.of("GET"), registry.methods("/ready"));
        assertEquals(Set.of("GET"), registry.methods("/health"));
        assertEquals(Set.of("GET", "POST"), registry.methods("/metrics"));
    }

    @Test
    void rendersReadinessStatus() {
        String json = HealthRoutes.readinessJson(CoreServiceConfig.fromEnvironment(), true);

        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"databaseReady\":true"));
        assertTrue(json.contains("\"databaseEffectiveBackend\""));
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
