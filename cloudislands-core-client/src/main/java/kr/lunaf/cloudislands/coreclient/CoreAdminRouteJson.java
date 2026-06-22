package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

final class CoreAdminRouteJson {
    private CoreAdminRouteJson() {
    }

    static AdminRouteDebugView debug(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminRouteSessionView> sessions = CoreJson.objects(root, "sessions").stream()
            .map(session -> new AdminRouteSessionView(
                CoreJson.text(session, "playerUuid"),
                CoreJson.text(session, "ticketId"),
                CoreJson.text(session, "targetNode"),
                CoreJson.text(session, "targetServerName"),
                CoreJson.text(session, "nonce"),
                CoreJson.text(session, "expiresAt")
            ))
            .toList();
        List<AdminRouteTicketView> tickets = CoreJson.objects(root, "tickets").stream()
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
            CoreJson.text(ticket, "ticketId"),
            CoreJson.text(ticket, "playerUuid"),
            CoreJson.text(ticket, "islandId"),
            CoreJson.text(ticket, "action"),
            CoreJson.text(ticket, "state"),
            CoreJson.text(ticket, "targetNode"),
            CoreJson.text(ticket, "targetWorld"),
            CoreJson.text(ticket, "targetServerName"),
            CoreJson.text(ticket, "targetType"),
            CoreJson.text(ticket, "homeName"),
            CoreJson.text(ticket, "warpName"),
            CoreJson.text(ticket, "expiresAt"),
            CoreJson.text(ticket, "nonce")
        ));
    }

    static AdminRouteClearView clear(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new AdminRouteClearView(CoreJson.bool(root, "clearedSession"), CoreJson.bool(root, "clearedTicket"), CoreJson.text(root, "reason"));
    }

    private static Map<?, ?> ticketObject(Map<?, ?> root) {
        if (root.containsKey("ticketId")) {
            return root;
        }
        return CoreJson.objectValue(root, "ticket");
    }
}
