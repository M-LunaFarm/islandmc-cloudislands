package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.session.InMemoryRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.ticket.InMemoryRouteTicketStore;
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

    @Test
    void publishSessionAuditsAndPublishesRouteSessionEvent() throws Exception {
        Fixture fixture = fixture();
        UUID playerUuid = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        RouteTicket ticket = routeTicket(playerUuid, ticketId, "island-1", RouteTicketState.READY);
        fixture.tickets.save(ticket);

        TestExchange exchange = fixture.invoke("/v1/routes/session", """
            {"ticketId":"%s","playerUuid":"%s","targetNode":"island-1","nonce":"nonce-1"}
            """.formatted(ticketId, playerUuid));

        assertEquals(202, exchange.status());
        assertTrue(fixture.audit.toJson().contains("ROUTE_SESSION_PUBLISH"));
        assertTrue(fixture.audit.toJson().contains(ticketId.toString()));
        assertTrue(fixture.events.toJson().contains("ROUTE_SESSION_PUBLISHED"));
        assertTrue(fixture.events.toJson().contains("\"targetServerName\":\"island-velocity-1\""));
    }

    @Test
    void consumeSessionWrongNodeAuditsAndPublishesMismatchEvent() throws Exception {
        Fixture fixture = fixture();
        UUID playerUuid = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        fixture.sessions.put(routeTicket(playerUuid, ticketId, "island-1", RouteTicketState.READY));

        TestExchange exchange = fixture.invoke("/v1/routes/session/consume", """
            {"playerUuid":"%s","nodeId":"island-2","ticketId":"%s","nonce":"nonce-1","reportMissing":true}
            """.formatted(playerUuid, ticketId));

        assertEquals(404, exchange.status());
        assertTrue(fixture.audit.toJson().contains("ROUTE_SESSION_CONSUME_FAILED"));
        assertTrue(fixture.audit.toJson().contains("SESSION_EXACT_MISMATCH"));
        assertTrue(fixture.events.toJson().contains("ROUTE_TICKET_FAILED"));
        assertTrue(fixture.events.toJson().contains("\"requestedNode\":\"island-2\""));
    }

    @Test
    void adminClearAuditsAndPublishesRouteClearEvent() throws Exception {
        Fixture fixture = fixture();
        UUID playerUuid = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        fixture.tickets.save(routeTicket(playerUuid, ticketId, "island-1", RouteTicketState.READY));
        fixture.sessions.put(routeTicket(playerUuid, ticketId, "island-1", RouteTicketState.READY));

        TestExchange exchange = fixture.invoke("/v1/admin/routes/clear", """
            {"playerUuid":"%s","reason":"DOWN_NODE"}
            """.formatted(playerUuid));

        assertEquals(202, exchange.status());
        assertTrue(exchange.body().contains("\"reason\":\"DOWN_NODE\""));
        assertTrue(fixture.audit.toJson().contains("ROUTE_CLEAR"));
        assertTrue(fixture.audit.toJson().contains("DOWN_NODE"));
        assertTrue(fixture.events.toJson().contains("ROUTE_TICKET_CLEARED"));
        assertTrue(fixture.events.toJson().contains("\"clearedTicket\":\"true\""));
    }

    private static Fixture fixture() {
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        InMemoryRouteTicketStore tickets = new InMemoryRouteTicketStore(clock);
        InMemoryRouteSessionStore sessions = new InMemoryRouteSessionStore(clock);
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        RecordingRegistry registry = new RecordingRegistry();
        new RouteTicketRoutes(null, tickets, sessions, audit, events).register(registry);
        return new Fixture(registry, tickets, sessions, audit, events);
    }

    private static RouteTicket routeTicket(UUID playerUuid, UUID ticketId, String targetNode, RouteTicketState state) {
        return new RouteTicket(
            ticketId,
            playerUuid,
            RouteAction.HOME,
            UUID.randomUUID(),
            targetNode,
            "ci_shard_001",
            state,
            Instant.now().plusSeconds(60),
            "nonce-1",
            Map.of("targetServerName", "island-velocity-1")
        );
    }

    private record Fixture(
        RecordingRegistry registry,
        InMemoryRouteTicketStore tickets,
        InMemoryRouteSessionStore sessions,
        InMemoryAuditLogger audit,
        InMemoryGlobalEventPublisher events
    ) {
        TestExchange invoke(String path, String body) throws IOException {
            TestExchange exchange = new TestExchange(path, body);
            registry.handler(path).handle(exchange);
            return exchange;
        }
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();
        private final Map<String, HttpHandler> handlers = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
            handlers.put(path, handler);
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
            handlers.put(path, handler);
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }

        HttpHandler handler(String path) {
            return handlers.get(path);
        }
    }

    private static final class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayInputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final URI uri;
        private int status;

        private TestExchange(String path, String body) {
            this.uri = URI.create(path);
            this.requestBody = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return uri;
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.status = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private int status() {
            return status;
        }

        private String body() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }
    }
}
