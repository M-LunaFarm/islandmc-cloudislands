package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.InMemoryNodeRegistry;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.job.InMemoryIslandJobPublisher;
import kr.lunaf.cloudislands.coreservice.session.InMemoryRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
import org.junit.jupiter.api.Test;

class AdminSupportBundleRoutesTest {
    @Test
    void registersSupportBundleEndpointAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();
        AdminSupportBundleRoutes routes = new AdminSupportBundleRoutes(null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register(registry));

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/support-bundle"));
    }

    @Test
    void rendersRequiredSupportBundleSectionsWithRedactedRouteNonce() {
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        tickets.save(new RouteTicket(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            RouteAction.HOME,
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            "island-1",
            "world",
            RouteTicketState.READY,
            Instant.parse("2026-01-01T00:00:00Z"),
            "super-secret-nonce",
            Map.of("targetServerName", "Island-1")
        ));
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        events.publish("ROUTE_TICKET_FAILED", Map.of("targetNode", "island-1", "reason", "TEST_FAILURE"));

        String json = AdminSupportBundleRoutes.supportBundleJson(
            null,
            new InMemoryNodeRegistry(2),
            new InMemoryIslandJobPublisher(),
            tickets,
            new InMemoryRouteSessionStore(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
            events,
            null,
            null,
            null
        );
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
        Map<?, ?> routeTicketState = SimpleJson.object(root.get("routeTicketState"));
        Map<?, ?> configRedaction = SimpleJson.object(root.get("configRedaction"));
        List<?> failures = SimpleJson.list(root.get("recentFailures"));

        assertTrue(root.containsKey("version"));
        assertTrue(root.containsKey("nodeState"));
        assertTrue(root.containsKey("coreRedisDbStorage"));
        assertTrue(root.containsKey("routeTicketState"));
        assertTrue(root.containsKey("configRedaction"));
        assertTrue(root.containsKey("recentFailures"));
        assertTrue(Boolean.TRUE.equals(configRedaction.get("secretsRedacted")));
        assertEquals("<redacted>", SimpleJson.text(configRedaction.get("redactionMarker")));
        assertTrue(routeTicketState.toString().contains("hidden"));
        assertFalse(json.contains("super-secret-nonce"));
        assertFalse(json.contains("\"coreToken\":"));
        assertFalse(json.contains("\"adminToken\":"));
        assertFalse(failures.isEmpty());
        assertEquals("ROUTE_TICKET_FAILED", SimpleJson.text(SimpleJson.object(failures.get(0)).get("type")));
    }

    @Test
    void routeNonceMaskerLeavesOnlyHiddenNonceValues() {
        String masked = AdminSupportBundleRoutes.maskRouteNonces("{\"nonce\":\"alpha\",\"nested\":{\"nonce\":\"beta\"}}");

        assertEquals("{\"nonce\":\"hidden\",\"nested\":{\"nonce\":\"hidden\"}}", masked);
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
