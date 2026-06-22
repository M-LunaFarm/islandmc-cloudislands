package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

final class CoreRouteJson {
    private CoreRouteJson() {
    }

    static RouteTicket routeTicketResult(String body) {
        if (body == null || body.isBlank()) {
            throw new CoreApiException("ROUTE_FAILED", "Route ticket could not be created");
        }
        Object parsed = SimpleJson.parse(body);
        if (!containsField(parsed, "ticketId")) {
            Map<?, ?> root = SimpleJson.object(parsed);
            String code = SimpleJson.text(root.get("code"));
            String message = SimpleJson.text(root.get("message"));
            throw new CoreApiException(code.isBlank() ? "ROUTE_FAILED" : code, message.isBlank() ? "Route ticket could not be created" : message);
        }
        RouteTicket ticket = routeTicket(body);
        if (ticket == null) {
            throw new CoreApiException("ROUTE_FAILED", "Route ticket could not be parsed");
        }
        return ticket;
    }

    static PlayerRouteSession routeSession(String json) {
        Map<?, ?> root = object(json);
        return new PlayerRouteSession(
            uuid(root, "playerUuid", new UUID(0L, 0L)),
            uuid(root, "ticketId", new UUID(0L, 0L)),
            text(root, "targetNode", ""),
            text(root, "targetServerName", ""),
            text(root, "nonce", ""),
            Instant.parse(text(root, "expiresAt", Instant.now().toString()))
        );
    }

    static RouteTicket nestedRouteTicket(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));
        Map<?, ?> nested = SimpleJson.object(root.get(field));
        return nested.isEmpty() ? null : routeTicketObject(nested);
    }

    static RouteTicket routeTicket(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Map<?, ?> ticket = ticketObject(SimpleJson.parse(json));
        return ticket.isEmpty() ? null : routeTicketObject(ticket);
    }

    private static RouteTicket routeTicketObject(Map<?, ?> ticket) {
        UUID ticketId = uuid(ticket, "ticketId", UUID.randomUUID());
        UUID playerUuid = uuid(ticket, "playerUuid", new UUID(0L, 0L));
        UUID islandId = uuid(ticket, "islandId", new UUID(0L, 0L));
        RouteAction action = enumValue(RouteAction.class, text(ticket, "action", "HOME"), RouteAction.HOME);
        RouteTicketState state = enumValue(RouteTicketState.class, text(ticket, "state", "READY"), RouteTicketState.READY);
        String targetNode = text(ticket, "targetNode", "");
        String targetWorld = text(ticket, "targetWorld", "ci_shard_001");
        String nonce = text(ticket, "nonce", "");
        String serverName = text(ticket, "targetServerName", targetNode);
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetServerName", serverName);
        putIfPresent(payload, ticket, "targetType");
        putIfPresent(payload, ticket, "homeName");
        putIfPresent(payload, ticket, "warpName");
        putIfPresent(payload, ticket, "localX");
        putIfPresent(payload, ticket, "localY");
        putIfPresent(payload, ticket, "localZ");
        putIfPresent(payload, ticket, "yaw");
        putIfPresent(payload, ticket, "pitch");
        Instant expiresAt = Instant.parse(text(ticket, "expiresAt", Instant.now().plusSeconds(30).toString()));
        return new RouteTicket(ticketId, playerUuid, action, islandId, targetNode, targetWorld, state, expiresAt, nonce, Map.copyOf(payload));
    }

    private static Map<?, ?> ticketObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("ticketId")) {
                return map;
            }
            Map<?, ?> nestedTicket = SimpleJson.object(map.get("ticket"));
            if (!nestedTicket.isEmpty()) {
                return nestedTicket;
            }
            for (Object nested : map.values()) {
                Map<?, ?> found = ticketObject(nested);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        if (value instanceof List<?> list) {
            for (Object nested : list) {
                Map<?, ?> found = ticketObject(nested);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return Map.of();
    }

    private static boolean containsField(Object value, String key) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(key)) {
                return true;
            }
            for (Object nested : map.values()) {
                if (containsField(nested, key)) {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof List<?> list) {
            for (Object nested : list) {
                if (containsField(nested, key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<?, ?> object(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        return SimpleJson.object(SimpleJson.parse(body));
    }

    private static UUID uuid(Map<?, ?> root, String key, UUID fallback) {
        String value = SimpleJson.text(root.get(key));
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String text(Map<?, ?> root, String key, String fallback) {
        String value = SimpleJson.text(root.get(key));
        return value.isBlank() ? fallback : value;
    }

    private static void putIfPresent(Map<String, String> payload, Map<?, ?> ticket, String field) {
        if (ticket.containsKey(field)) {
            payload.put(field, SimpleJson.text(ticket.get(field)));
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
