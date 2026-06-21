package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreAdminRouteJson {
    private CoreAdminRouteJson() {
    }

    static AdminRouteDebugView debug(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminRouteSessionView> sessions = SimpleJson.list(root.get("sessions")).stream()
            .map(SimpleJson::object)
            .map(session -> new AdminRouteSessionView(
                text(session, "playerUuid"),
                text(session, "ticketId"),
                text(session, "targetNode"),
                text(session, "targetServerName"),
                text(session, "nonce"),
                text(session, "expiresAt")
            ))
            .toList();
        List<AdminRouteTicketView> tickets = SimpleJson.list(root.get("tickets")).stream()
            .map(SimpleJson::object)
            .map(CoreAdminRouteJson::ticket)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        return new AdminRouteDebugView(sessions, tickets);
    }

    static Optional<AdminRouteTicketView> ticket(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Map<?, ?> root = CoreJson.object(body);
        if (root.containsKey("code") || root.containsKey("error")) {
            return Optional.empty();
        }
        Map<?, ?> ticket = ticketObject(root);
        if (ticket.isEmpty()) {
            return Optional.empty();
        }
        return ticket(ticket);
    }

    private static Optional<AdminRouteTicketView> ticket(Map<?, ?> ticket) {
        if (ticket.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new AdminRouteTicketView(
            text(ticket, "ticketId"),
            text(ticket, "playerUuid"),
            text(ticket, "islandId"),
            text(ticket, "action"),
            text(ticket, "state"),
            text(ticket, "targetNode"),
            text(ticket, "targetWorld"),
            text(ticket, "targetServerName"),
            text(ticket, "targetType"),
            text(ticket, "homeName"),
            text(ticket, "warpName"),
            text(ticket, "expiresAt"),
            text(ticket, "nonce")
        ));
    }

    static AdminRouteClearView clear(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new AdminRouteClearView(bool(root, "clearedSession"), bool(root, "clearedTicket"), text(root, "reason"));
    }

    private static Map<?, ?> ticketObject(Map<?, ?> root) {
        if (root.containsKey("ticketId")) {
            return root;
        }
        return SimpleJson.object(root.get("ticket"));
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }
}
