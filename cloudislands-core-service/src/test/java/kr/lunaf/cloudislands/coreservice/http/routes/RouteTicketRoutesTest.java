package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;
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
    void masksRouteNonces() {
        String masked = RouteTicketRoutes.maskRouteNonces("{\"nonce\":\"secret\",\"nested\":{\"nonce\":\"second\"}}");

        assertFalse(masked.contains("secret"));
        assertFalse(masked.contains("second"));
        assertTrue(masked.contains("\"nonce\":\"hidden\""));
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
}
