package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpHandler;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class AdminIslandLifecycleRoutesTest {
    @Test
    void registersAdminIslandLifecycleRoutesAsPostOnly() {
        RecordingRegistry routes = new RecordingRegistry();
        RecordingRegistry prefixes = new RecordingRegistry();

        new AdminIslandLifecycleRoutes(null, null, null, null, null, null, null).register(routes, prefixes);

        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/activate"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/deactivate"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/migrate"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/save"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/snapshot"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/restore"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/rollback"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/quarantine"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/info"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/where"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/delete"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/islands/repair"));
        assertEquals(Set.of("POST"), prefixes.methods("/v1/admin/islands/"));
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
