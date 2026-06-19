package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.coreservice.config.CoreServiceConfig;
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
    void rendersReadinessStatus() {
        String json = HealthRoutes.readinessJson(CoreServiceConfig.fromEnvironment(), true);

        assertTrue(json.contains("\"status\":\"UP\""));
        assertTrue(json.contains("\"databaseReady\":true"));
        assertTrue(json.contains("\"databaseEffectiveBackend\""));
    }
}
