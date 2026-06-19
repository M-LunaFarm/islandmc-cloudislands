package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.boolValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.countObjects;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;

import java.util.ArrayList;
import java.util.List;

public final class VelocityRouteMessageFormatter {
    private final VelocityRoutePrivacyFormatter routePrivacy;

    public VelocityRouteMessageFormatter(VelocityRoutePrivacyFormatter routePrivacy) {
        this.routePrivacy = routePrivacy == null ? new VelocityRoutePrivacyFormatter(true) : routePrivacy;
    }

    public String debug(String body) {
        String sessions = arrayValue(body, "sessions");
        String tickets = arrayValue(body, "tickets");
        List<String> sessionEntries = new ArrayList<>();
        List<String> ticketEntries = new ArrayList<>();
        collectSessionSummaries(sessions, sessionEntries, 5);
        collectTicketSummaries(tickets, ticketEntries, 5);
        return "Routes: sessions=" + countObjects(sessions)
            + (sessionEntries.isEmpty() ? "" : " [" + String.join(" | ", sessionEntries) + "]")
            + " tickets=" + countObjects(tickets)
            + (ticketEntries.isEmpty() ? "" : " [" + String.join(" | ", ticketEntries) + "]");
    }

    public String ticket(String body) {
        if (body == null || body.isBlank()) {
            return "Route ticket: not found";
        }
        String code = jsonValue(body, "code");
        if (!code.isBlank()) {
            return "Route ticket: failed code=" + code;
        }
        return "Route ticket: " + ticketSummary(body);
    }

    public String clear(String body) {
        if (body == null || body.isBlank()) {
            return "Route clear: no response";
        }
        String reason = jsonValue(body, "reason");
        return "Route clear: session=" + boolValue(body, "clearedSession")
            + " ticket=" + boolValue(body, "clearedTicket")
            + (reason.isBlank() ? "" : " reason=" + reason);
    }

    private void collectSessionSummaries(String sessions, List<String> entries, int limit) {
        int index = 0;
        while (index < sessions.length() && entries.size() < limit) {
            int objectStart = sessions.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(sessions, objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = sessions.substring(objectStart, objectEnd + 1);
            String playerUuid = jsonValue(object, "playerUuid");
            String ticketId = jsonValue(object, "ticketId");
            String nodeId = jsonValue(object, "targetNode");
            String serverName = jsonValue(object, "targetServerName");
            String expiresAt = jsonValue(object, "expiresAt");
            entries.add(shortId(playerUuid)
                + " ticket=" + shortId(ticketId)
                + routePrivacy.routeNodeSuffix(nodeId)
                + routePrivacy.routeServerSuffix(serverName)
                + (expiresAt.isBlank() ? "" : " expires=" + expiresAt));
            index = objectEnd + 1;
        }
    }

    private void collectTicketSummaries(String tickets, List<String> entries, int limit) {
        int index = 0;
        while (index < tickets.length() && entries.size() < limit) {
            int objectStart = tickets.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(tickets, objectStart);
            if (objectEnd < 0) {
                break;
            }
            entries.add(ticketSummary(tickets.substring(objectStart, objectEnd + 1)));
            index = objectEnd + 1;
        }
    }

    public String ticketSummary(String object) {
        String ticketId = jsonValue(object, "ticketId");
        String action = jsonValue(object, "action");
        String state = jsonValue(object, "state");
        String islandId = jsonValue(object, "islandId");
        String nodeId = jsonValue(object, "targetNode");
        return shortId(ticketId)
            + " " + (action.isBlank() ? "UNKNOWN" : action)
            + " " + (state.isBlank() ? "UNKNOWN" : state)
            + (islandId.isBlank() ? "" : " 섬=" + shortId(islandId))
            + routePrivacy.routeNodeSuffix(nodeId);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
