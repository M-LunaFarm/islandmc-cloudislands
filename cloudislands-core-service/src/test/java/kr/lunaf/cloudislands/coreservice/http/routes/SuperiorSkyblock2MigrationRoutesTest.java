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

class SuperiorSkyblock2MigrationRoutesTest {
    @Test
    void registersMigrationEndpointGroup() {
        List<String> paths = new ArrayList<>();
        SuperiorSkyblock2MigrationRoutes routes = new SuperiorSkyblock2MigrationRoutes(false, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/admin/migrations/superiorskyblock2/scan"));
        assertTrue(paths.contains("/v1/admin/migrations/superiorskyblock2/status"));
        assertTrue(paths.contains("/v1/admin/migrations/superiorskyblock2/rollback"));
    }

    @Test
    void registersMigrationEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new SuperiorSkyblock2MigrationRoutes(false, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/scan"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/status"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/dryrun"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/extract"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/import"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/verify"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/migrations/superiorskyblock2/rollback"));
    }

    @Test
    void rendersDisabledMigrationResponse() {
        String json = SuperiorSkyblock2MigrationRoutes.disabledJson();

        assertTrue(json.contains("\"code\":\"MIGRATION_DISABLED\""));
        assertTrue(json.contains("\"state\":\"DISABLED\""));
        assertTrue(json.contains("\"sourcePlugin\":\"SuperiorSkyblock2\""));
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
