package kr.lunaf.cloudislands.coreservice.http.routes;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.RoutingOrchestrator;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.session.InMemoryRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.session.RedisRouteSessionStore;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionJson;
import kr.lunaf.cloudislands.coreservice.session.RouteSessionStore;
import kr.lunaf.cloudislands.coreservice.ticket.RouteTicketStore;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class RouteTicketRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final RoutingOrchestrator routing;
    private final RouteTicketStore tickets;
    private final RouteSessionStore sessions;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public RouteTicketRoutes(
        RoutingOrchestrator routing,
        RouteTicketStore tickets,
        RouteSessionStore sessions,
        AuditLogger audit,
        GlobalEventPublisher events
    ) {
        this.routing = routing;
        this.tickets = tickets;
        this.sessions = sessions;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/routes/session", this::publishSession);
        registry.route("/v1/routes/session/find", this::findSession);
        registry.route("/v1/routes/session/find-any", this::findAnySession);
        registry.route("/v1/routes/session/consume", this::consumeSession);
        registry.route("/v1/routes/ticket-status", this::ticketStatus);
        registry.route("/v1/routes/consume", exchange -> CoreHttpResponses.write(exchange, 200, routing.consumeTicketJson(CoreHttpResponses.readBody(exchange))));
        registry.route("/v1/admin/routes/debug", this::debug);
        registry.route("/v1/admin/routes/ticket", this::ticket);
        registry.route("/v1/admin/routes/clear", this::clear);
    }

    private void publishSession(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID ticketId = JsonFields.uuid(body, "ticketId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String targetNode = JsonFields.text(body, "targetNode", "");
        String nonce = JsonFields.text(body, "nonce", "");
        RouteTicket ticket = tickets.find(ticketId).orElse(null);
        if (ticket == null) {
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                "ticketId", ticketId.toString(),
                "playerUuid", playerUuid.toString(),
                "targetNode", targetNode,
                "reason", "SESSION_TICKET_NOT_FOUND"
            ));
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("TICKET_NOT_FOUND", "Route ticket was not found"));
            return;
        }
        if (ticket.state() != RouteTicketState.READY) {
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), ticketFailureFields(ticket, "SESSION_TICKET_NOT_READY"));
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("TICKET_NOT_READY", "Route ticket is not ready"));
            return;
        }
        if (ticket.expiresAt().isBefore(Instant.now())) {
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), ticketFailureFields(ticket, "SESSION_TICKET_EXPIRED"));
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("TICKET_EXPIRED", "Route ticket has expired"));
            return;
        }
        if (!ticket.playerUuid().equals(playerUuid) || !ticket.targetNode().equals(targetNode) || !ticket.nonce().equals(nonce)) {
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                "ticketId", ticket.ticketId().toString(),
                "playerUuid", playerUuid.toString(),
                "islandId", ticket.islandId().toString(),
                "action", ticket.action().name(),
                "targetNode", targetNode,
                "reason", "SESSION_TICKET_MISMATCH"
            ));
            CoreHttpResponses.write(exchange, 403, ApiResponses.error("TICKET_MISMATCH", "Route ticket fields do not match"));
            return;
        }
        sessions.put(ticket);
        audit.log(ticket.playerUuid(), "PLAYER", "ROUTE_SESSION_PUBLISH", "ROUTE", ticket.ticketId().toString(), Map.of(
            "islandId", ticket.islandId().toString(),
            "action", ticket.action().name(),
            "targetNode", ticket.targetNode(),
            "targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode())
        ));
        events.publish(CloudIslandEventType.ROUTE_SESSION_PUBLISHED.name(), Map.of(
            "ticketId", ticket.ticketId().toString(),
            "playerUuid", ticket.playerUuid().toString(),
            "islandId", ticket.islandId().toString(),
            "action", ticket.action().name(),
            "targetNode", ticket.targetNode(),
            "targetServerName", ticket.payload().getOrDefault("targetServerName", ticket.targetNode())
        ));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void findSession(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String nodeId = JsonFields.text(body, "nodeId", "");
        PlayerRouteSession session = sessions.find(playerUuid, nodeId).orElse(null);
        if (session == null) {
            PlayerRouteSession existing = sessions.findAny(playerUuid).orElse(null);
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), existing == null
                ? Map.of("playerUuid", playerUuid.toString(), "targetNode", nodeId, "reason", "SESSION_LOOKUP_NOT_FOUND")
                : Map.of("playerUuid", playerUuid.toString(), "ticketId", existing.ticketId().toString(), "targetNode", existing.targetNode(), "requestedNode", nodeId, "reason", "SESSION_LOOKUP_NODE_MISMATCH"));
        }
        CoreHttpResponses.write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
    }

    private void findAnySession(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        PlayerRouteSession session = sessions.findAny(playerUuid).orElse(null);
        CoreHttpResponses.write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
    }

    private void consumeSession(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String nodeId = JsonFields.text(body, "nodeId", "");
        String ticketIdText = JsonFields.text(body, "ticketId", "");
        String nonce = JsonFields.text(body, "nonce", "");
        boolean reportMissing = JsonFields.bool(body, "reportMissing", true);
        PlayerRouteSession session = ticketIdText.isBlank() && nonce.isBlank()
            ? sessions.consume(playerUuid, nodeId).orElse(null)
            : sessions.consume(playerUuid, nodeId, JsonFields.uuid(body, "ticketId", EMPTY_UUID), nonce).orElse(null);
        if (session != null) {
            audit.log(session.playerUuid(), "PLAYER", "ROUTE_SESSION_CONSUME", "ROUTE", session.ticketId().toString(), Map.of(
                "targetNode", session.targetNode(),
                "targetServerName", session.targetServerName()
            ));
        }
        if (session == null && reportMissing) {
            PlayerRouteSession existing = sessions.findAny(playerUuid).orElse(null);
            String reason = ticketIdText.isBlank() && nonce.isBlank() ? "SESSION_NODE_MISMATCH" : "SESSION_EXACT_MISMATCH";
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), existing == null
                ? Map.of("playerUuid", playerUuid.toString(), "targetNode", nodeId, "reason", "SESSION_NOT_FOUND")
                : Map.of("playerUuid", playerUuid.toString(), "ticketId", existing.ticketId().toString(), "targetNode", existing.targetNode(), "requestedNode", nodeId, "reason", reason));
            audit.log(playerUuid, "PLAYER", "ROUTE_SESSION_CONSUME_FAILED", "ROUTE", existing == null ? "" : existing.ticketId().toString(), existing == null
                ? Map.of("targetNode", nodeId, "reason", "SESSION_NOT_FOUND")
                : Map.of("targetNode", existing.targetNode(), "requestedNode", nodeId, "reason", reason));
        }
        CoreHttpResponses.write(exchange, session == null ? 404 : 200, session == null ? "" : sessionJson(session));
    }

    private void ticketStatus(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID ticketId = JsonFields.uuid(body, "ticketId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String nonce = JsonFields.text(body, "nonce", "");
        RouteTicket ticket = tickets.find(ticketId).orElse(null);
        boolean allowed = ticket != null && ticket.playerUuid().equals(playerUuid) && ticket.nonce().equals(nonce);
        if (!allowed) {
            events.publish(CloudIslandEventType.ROUTE_TICKET_FAILED.name(), Map.of(
                "ticketId", ticketId.toString(),
                "playerUuid", playerUuid.toString(),
                "islandId", ticket == null ? "" : ticket.islandId().toString(),
                "action", ticket == null ? "" : ticket.action().name(),
                "targetNode", ticket == null ? "" : ticket.targetNode(),
                "reason", "VERIFY_FAILED"
            ));
        }
        CoreHttpResponses.write(exchange, allowed ? 200 : 404, allowed ? RoutingOrchestrator.toJson(ticket) : ApiResponses.error("ROUTE_TICKET_NOT_FOUND", "Route ticket was not found"));
    }

    private void debug(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        if (playerUuid.equals(EMPTY_UUID)) {
            CoreHttpResponses.write(exchange, 200, maskRouteNonces(SimpleJson.stringify(Map.of(
                "sessions", SimpleJson.object(SimpleJson.parse(routeSessionsJson(sessions))),
                "tickets", SimpleJson.object(SimpleJson.parse(tickets.toJson()))
            ))));
            return;
        }
        PlayerRouteSession session = sessions.findAny(playerUuid).orElse(null);
        RouteTicket ticket = tickets.findLatestForPlayer(playerUuid).orElse(null);
        boolean found = session != null || ticket != null;
        CoreHttpResponses.write(exchange, found ? 200 : 404, found ? routeDebugJson(playerUuid, session, ticket) : ApiResponses.error("ROUTE_ROUTE_NOT_FOUND", "Route session or ticket was not found"));
    }

    private void ticket(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID ticketId = JsonFields.uuid(body, "ticketId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        RouteTicket ticket = ticketId.equals(EMPTY_UUID)
            ? tickets.findLatestForPlayer(playerUuid).orElse(null)
            : tickets.find(ticketId).orElse(null);
        CoreHttpResponses.write(exchange, ticket == null ? 404 : 200, ticket == null ? ApiResponses.error("ROUTE_TICKET_NOT_FOUND", "Route ticket was not found") : maskRouteNonces(RoutingOrchestrator.toJson(ticket)));
    }

    private void clear(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        UUID ticketId = JsonFields.uuid(body, "ticketId", EMPTY_UUID);
        String reason = JsonFields.text(body, "reason", "MANUAL_CLEAR");
        boolean clearedSession = !playerUuid.equals(EMPTY_UUID) && sessions.clear(playerUuid);
        UUID clearTicketId = ticketId.equals(EMPTY_UUID) && !playerUuid.equals(EMPTY_UUID)
            ? tickets.findLatestForPlayer(playerUuid).map(RouteTicket::ticketId).orElse(EMPTY_UUID)
            : ticketId;
        boolean clearedTicket = !clearTicketId.equals(EMPTY_UUID) && tickets.clear(clearTicketId);
        String clearReason = reason == null || reason.isBlank() ? "MANUAL_CLEAR" : reason;
        audit.log(EMPTY_UUID, "MANUAL_CLEAR".equals(clearReason) ? "ADMIN" : "SYSTEM", "ROUTE_CLEAR", "ROUTE", playerUuid.toString(), Map.of(
            "ticketId", clearTicketId.toString(),
            "reason", clearReason,
            "clearedSession", Boolean.toString(clearedSession),
            "clearedTicket", Boolean.toString(clearedTicket)
        ));
        events.publish(CloudIslandEventType.ROUTE_TICKET_CLEARED.name(), Map.of(
            "playerUuid", playerUuid.toString(),
            "ticketId", clearTicketId.toString(),
            "reason", clearReason,
            "clearedSession", Boolean.toString(clearedSession),
            "clearedTicket", Boolean.toString(clearedTicket)
        ));
        CoreHttpResponses.write(exchange, 202, SimpleJson.stringify(Map.of(
            "clearedSession", clearedSession,
            "clearedTicket", clearedTicket,
            "reason", clearReason
        )));
    }

    private Map<String, String> ticketFailureFields(RouteTicket ticket, String reason) {
        return Map.of(
            "ticketId", ticket.ticketId().toString(),
            "playerUuid", ticket.playerUuid().toString(),
            "islandId", ticket.islandId().toString(),
            "action", ticket.action().name(),
            "targetNode", ticket.targetNode(),
            "reason", reason
        );
    }

    static String routeDebugJson(UUID playerUuid, PlayerRouteSession session, RouteTicket ticket) {
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("playerUuid", playerUuid);
        root.put("session", session == null ? null : maskedJsonObject(sessionJson(session)));
        root.put("ticket", ticket == null ? null : maskedJsonObject(RoutingOrchestrator.toJson(ticket)));
        return SimpleJson.stringify(root);
    }

    static String maskRouteNonces(String json) {
        try {
            Object parsed = SimpleJson.parse(json);
            if (parsed == null) {
                return json;
            }
            return SimpleJson.stringify(maskNonceValue(parsed));
        } catch (RuntimeException ignored) {
            return json;
        }
    }

    static String routeSessionsJson(RouteSessionStore sessions) {
        if (sessions instanceof InMemoryRouteSessionStore memorySessions) {
            return memorySessions.toJson();
        }
        if (sessions instanceof RedisRouteSessionStore redisSessions) {
            return redisSessions.toJson();
        }
        return "{\"sessions\":[]}";
    }

    static String sessionJson(PlayerRouteSession session) {
        return RouteSessionJson.session(session);
    }

    private static Object maskedJsonObject(String json) {
        return SimpleJson.object(SimpleJson.parse(maskRouteNonces(json)));
    }

    private static Object maskNonceValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> masked = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = SimpleJson.text(entry.getKey());
                masked.put(key, key.equals("nonce") ? "hidden" : maskNonceValue(entry.getValue()));
            }
            return masked;
        }
        if (value instanceof List<?> list) {
            java.util.ArrayList<Object> masked = new java.util.ArrayList<>();
            for (Object item : list) {
                masked.add(maskNonceValue(item));
            }
            return masked;
        }
        return value;
    }
}
