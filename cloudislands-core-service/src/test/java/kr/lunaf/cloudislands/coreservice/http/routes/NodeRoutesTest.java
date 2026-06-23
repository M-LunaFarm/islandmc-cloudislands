package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class NodeRoutesTest {
    @Test
    void registersNodeEndpointGroup() {
        List<String> paths = new ArrayList<>();
        NodeRoutes routes = new NodeRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/nodes"));
        assertTrue(paths.contains("/v1/admin/nodes/islands"));
    }

    @Test
    void registersNodeEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new NodeRoutes(null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/storage"));
        assertEquals(Set.of("POST"), registry.methods("/v1/nodes"));
        assertEquals(Set.of("POST"), registry.methods("/v1/nodes/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/nodes/islands"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/nodes/list"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/nodes/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/nodes/islands"));
    }

    @Test
    void rendersNodeIslandRuntimeList() {
        IslandRuntimeSnapshot runtime = new IslandRuntimeSnapshot(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            IslandState.ACTIVE,
            "island-1",
            "ci_world",
            2,
            3,
            "worker",
            7L,
            Instant.parse("2026-01-01T00:00:00Z"),
            Instant.parse("2026-01-01T00:00:05Z")
        );

        String json = NodeRoutes.nodeIslandsJson("island-1", List.of(runtime));
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
        Map<?, ?> renderedRuntime = SimpleJson.object(SimpleJson.list(root.get("islands")).get(0));

        assertEquals("island-1", SimpleJson.text(root.get("nodeId")));
        assertEquals(1, ((Number) root.get("count")).intValue());
        assertEquals("ACTIVE", SimpleJson.text(renderedRuntime.get("state")));
        assertEquals("ci_world", SimpleJson.text(renderedRuntime.get("activeWorld")));
        assertEquals(7L, ((Number) renderedRuntime.get("fencingToken")).longValue());
        assertEquals("2026-01-01T00:00:05Z", SimpleJson.text(renderedRuntime.get("lastHeartbeat")));
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
