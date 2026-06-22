package kr.lunaf.cloudislands.velocity.message;

import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.coreclient.AdminRouteDebugView;
import kr.lunaf.cloudislands.coreclient.AdminRouteClearView;
import kr.lunaf.cloudislands.coreclient.AdminRouteSessionView;
import kr.lunaf.cloudislands.coreclient.AdminRouteTicketView;

public final class VelocityRouteMessageFormatter {
    private final VelocityRoutePrivacyFormatter routePrivacy;

    public VelocityRouteMessageFormatter(VelocityRoutePrivacyFormatter routePrivacy) {
        this.routePrivacy = routePrivacy == null ? new VelocityRoutePrivacyFormatter(true) : routePrivacy;
    }

    public String debug(AdminRouteDebugView view) {
        if (view == null) {
            return "Routes: sessions=0 tickets=0";
        }
        List<String> sessionEntries = view.sessions().stream()
            .limit(5)
            .map(this::sessionSummary)
            .toList();
        List<String> ticketEntries = view.tickets().stream()
            .limit(5)
            .map(this::ticketSummary)
            .toList();
        return "Routes: sessions=" + view.sessions().size()
            + (sessionEntries.isEmpty() ? "" : " [" + String.join(" | ", sessionEntries) + "]")
            + " tickets=" + view.tickets().size()
            + (ticketEntries.isEmpty() ? "" : " [" + String.join(" | ", ticketEntries) + "]");
    }

    public String ticket(Optional<AdminRouteTicketView> ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return "Route ticket: not found";
        }
        return "Route ticket: " + ticketSummary(ticket.get());
    }

    public String clear(AdminRouteClearView view) {
        if (view == null) {
            return "Route clear: no response";
        }
        return "Route clear: session=" + view.clearedSession()
            + " ticket=" + view.clearedTicket()
            + (view.reason().isBlank() ? "" : " reason=" + view.reason());
    }

    private String sessionSummary(AdminRouteSessionView session) {
        return shortId(session.playerUuid())
            + " ticket=" + shortId(session.ticketId())
            + routePrivacy.routeNodeSuffix(session.targetNode())
            + routePrivacy.routeServerSuffix(session.targetServerName())
            + (session.expiresAt().isBlank() ? "" : " expires=" + session.expiresAt());
    }

    public String ticketSummary(AdminRouteTicketView ticket) {
        return shortId(ticket.ticketId())
            + " " + (ticket.action().isBlank() ? "UNKNOWN" : ticket.action())
            + " " + (ticket.state().isBlank() ? "UNKNOWN" : ticket.state())
            + (ticket.islandId().isBlank() ? "" : " 섬=" + shortId(ticket.islandId()))
            + routePrivacy.routeNodeSuffix(ticket.targetNode());
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
