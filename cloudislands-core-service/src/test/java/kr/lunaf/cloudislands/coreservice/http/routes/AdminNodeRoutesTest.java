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

class AdminNodeRoutesTest {
    @Test
    void registersAdminNodeEndpointGroup() {
        List<String> routes = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        AdminNodeRoutes adminNodes = new AdminNodeRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> adminNodes.register((path, handler) -> routes.add(path), (path, handler) -> prefixes.add(path)));

        assertEquals(5, routes.size());
        assertTrue(routes.contains("/v1/admin/nodes/drain"));
        assertTrue(routes.contains("/v1/admin/nodes/sweep"));
        assertEquals(List.of("/v1/admin/nodes/"), prefixes);
    }

    @Test
    void registersAdminNodeMutationsAsPostOnly() {
        RecordingRegistry routes = new RecordingRegistry();
        RecordingRegistry prefixes = new RecordingRegistry();

        new AdminNodeRoutes(null, null, null, null, null).register(routes, prefixes);

        assertEquals(Set.of("POST"), routes.methods("/v1/admin/nodes/drain"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/nodes/undrain"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/nodes/kickall"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/nodes/shutdown-safe"));
        assertEquals(Set.of("POST"), routes.methods("/v1/admin/nodes/sweep"));
        assertEquals(Set.of("POST"), prefixes.methods("/v1/admin/nodes/"));
    }

    @Test
    void rendersNodeLifecyclePolicy() {
        String json = AdminNodeRoutes.nodeLifecycleJson("node-1", "DRAINING", "DRAIN");

        assertTrue(json.contains("\"accepted\":true"));
        assertTrue(json.contains("\"nodeId\":\"node-1\""));
        assertTrue(json.contains("\"operation\":\"DRAIN\""));
        assertEquals("DRAIN", AdminNodeRoutes.nodeLifecycleFields("node-1", "DRAINING", "DRAIN").get("operation"));
    }

    @Test
    void rendersNodeSweepNodeList() {
        List<?> nodes = SimpleJson.list(SimpleJson.parse(AdminNodeRoutes.nodesJson(List.of("node-1", "node-\"2"))));

        assertEquals(List.of("node-1", "node-\"2"), nodes);
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
