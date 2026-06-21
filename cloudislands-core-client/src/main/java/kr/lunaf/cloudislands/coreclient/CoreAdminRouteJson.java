package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.Optional;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class CoreAdminRouteJson {
    private CoreAdminRouteJson() {
    }

    static Optional<AdminRouteTicketView> ticket(String body) {
        if (body == null || body.isBlank()) {
            return Optional.empty();
        }
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        if (root.containsKey("code") || root.containsKey("error")) {
            return Optional.empty();
        }
        Map<?, ?> ticket = ticketObject(root);
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
            text(ticket, "targetServerName"),
            text(ticket, "targetType"),
            text(ticket, "homeName"),
            text(ticket, "warpName"),
            text(ticket, "expiresAt")
        ));
    }

    static AdminRouteClearView clear(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body == null || body.isBlank() ? "{}" : body));
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
