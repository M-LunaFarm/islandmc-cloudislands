package kr.lunaf.cloudislands.velocity.message;

import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.arrayValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.jsonValue;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.matchingObjectEnd;
import static kr.lunaf.cloudislands.velocity.message.VelocityJsonFields.objectValue;

import java.util.ArrayList;
import java.util.List;

public final class VelocityEventMessageFormatter {
    private final VelocityRoutePrivacyFormatter routePrivacy;

    public VelocityEventMessageFormatter(VelocityRoutePrivacyFormatter routePrivacy) {
        this.routePrivacy = routePrivacy == null ? new VelocityRoutePrivacyFormatter(true) : routePrivacy;
    }

    public String events(String body) {
        String events = arrayValue(body, "events");
        if (events.isBlank()) {
            return "Events: empty";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < events.length() && entries.size() < 10) {
            int objectStart = events.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(events, objectStart);
            if (objectEnd < 0) {
                break;
            }
            entries.add(eventEntry(events.substring(objectStart, objectEnd + 1)));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Events: empty" : "Events: " + String.join(" | ", entries);
    }

    public String audit(String body) {
        String audit = arrayValue(body, "audit");
        if (audit.isBlank()) {
            return "Audit: empty";
        }
        List<String> entries = new ArrayList<>();
        int index = 0;
        while (index < audit.length() && entries.size() < 10) {
            int objectStart = audit.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = matchingObjectEnd(audit, objectStart);
            if (objectEnd < 0) {
                break;
            }
            entries.add(auditEntry(audit.substring(objectStart, objectEnd + 1)));
            index = objectEnd + 1;
        }
        return entries.isEmpty() ? "Audit: empty" : "Audit: " + String.join(" | ", entries);
    }

    private String eventEntry(String object) {
        String type = jsonValue(object, "type");
        String occurredAt = jsonValue(object, "occurredAt");
        String fields = objectValue(object, "fields");
        String islandId = jsonValue(fields, "islandId");
        String ticketId = jsonValue(fields, "ticketId");
        String playerUuid = jsonValue(fields, "playerUuid");
        String action = jsonValue(fields, "action");
        String reason = jsonValue(fields, "reason");
        String requestedNode = jsonValue(fields, "requestedNode");
        String clearedSession = jsonValue(fields, "clearedSession");
        String clearedTicket = jsonValue(fields, "clearedTicket");
        String nodeId = jsonValue(fields, "nodeId");
        if (nodeId.isBlank()) {
            nodeId = jsonValue(fields, "targetNode");
        }
        return (type.isBlank() ? "UNKNOWN_EVENT" : type)
            + (islandId.isBlank() ? "" : " 섬=" + islandId)
            + (ticketId.isBlank() ? "" : " ticket=" + shortId(ticketId))
            + (playerUuid.isBlank() ? "" : " player=" + shortId(playerUuid))
            + (action.isBlank() ? "" : " action=" + action)
            + (reason.isBlank() ? "" : " reason=" + reason)
            + routePrivacy.routeRequestedNodeSuffix(requestedNode)
            + (clearedSession.isBlank() ? "" : " session=" + clearedSession)
            + (clearedTicket.isBlank() ? "" : " ticketCleared=" + clearedTicket)
            + routePrivacy.routeNodeSuffix(nodeId)
            + (occurredAt.isBlank() ? "" : " at=" + occurredAt);
    }

    private String auditEntry(String object) {
        String action = jsonValue(object, "action");
        String actorType = jsonValue(object, "actorType");
        String targetType = jsonValue(object, "targetType");
        String targetId = jsonValue(object, "targetId");
        String createdAt = jsonValue(object, "createdAt");
        return (action.isBlank() ? "UNKNOWN_ACTION" : action)
            + (targetType.isBlank() && targetId.isBlank() ? "" : " target=" + targetType + ":" + targetId)
            + (actorType.isBlank() ? "" : " actor=" + actorType)
            + (createdAt.isBlank() ? "" : " at=" + createdAt);
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
