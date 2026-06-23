package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;
import org.junit.jupiter.api.Test;

class RouteTicketRoutesTest {
    @Test
    void registersRouteTicketEndpointGroup() {
        List<String> paths = new ArrayList<>();
        RouteTicketRoutes routes = new RouteTicketRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(9, paths.size());
        assertTrue(paths.contains("/v1/routes/session"));
        assertTrue(paths.contains("/v1/admin/routes/clear"));
    }

    @Test
    void registersRouteTicketEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new RouteTicketRoutes(null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/routes/session"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/session/find"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/session/find-any"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/session/consume"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/ticket-status"));
        assertEquals(Set.of("POST"), registry.methods("/v1/routes/consume"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/routes/debug"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/routes/ticket"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/routes/clear"));
    }

    @Test
    void masksRouteNonces() {
        String masked = RouteTicketRoutes.maskRouteNonces("{\"nonce\":\"sec\\\"ret\",\"nested\":{\"nonce\":\"second\"}}");
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(masked));
        Map<?, ?> nested = SimpleJson.object(root.get("nested"));

        assertFalse(masked.contains("sec\\\"ret"));
        assertFalse(masked.contains("second"));
        assertEquals("hidden", SimpleJson.text(root.get("nonce")));
        assertEquals("hidden", SimpleJson.text(nested.get("nonce")));
    }

    @Test
    void rendersSessionJson() {
        PlayerRouteSession session = new PlayerRouteSession(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "island-1, \"east\"",
            "Island-1, \"East\"",
            "nonce\"value",
            Instant.parse("2026-01-01T00:00:00Z")
        );

        String json = RouteTicketRoutes.sessionJson(session);
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));

        assertEquals("island-1, \"east\"", SimpleJson.text(root.get("targetNode")));
        assertEquals("Island-1, \"East\"", SimpleJson.text(root.get("targetServerName")));
        assertEquals("nonce\"value", SimpleJson.text(root.get("nonce")));
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
