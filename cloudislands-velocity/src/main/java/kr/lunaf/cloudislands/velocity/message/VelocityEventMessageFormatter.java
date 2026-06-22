package kr.lunaf.cloudislands.velocity.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.coreclient.AdminAuditEntryView;
import kr.lunaf.cloudislands.coreclient.AdminEventStreamView;
import kr.lunaf.cloudislands.coreclient.AdminEventView;

public final class VelocityEventMessageFormatter {
    private final VelocityRoutePrivacyFormatter routePrivacy;

    public VelocityEventMessageFormatter(VelocityRoutePrivacyFormatter routePrivacy) {
        this.routePrivacy = routePrivacy == null ? new VelocityRoutePrivacyFormatter(true) : routePrivacy;
    }

    public String events(AdminEventStreamView view) {
        if (view == null || view.events().isEmpty()) {
            return "Events: empty";
        }
        List<String> entries = new ArrayList<>();
        for (AdminEventView event : view.events()) {
            if (entries.size() >= 10) {
                break;
            }
            entries.add(eventEntry(event));
        }
        return entries.isEmpty() ? "Events: empty" : "Events: " + String.join(" | ", entries);
    }

    public String audit(List<AdminAuditEntryView> audit) {
        if (audit == null || audit.isEmpty()) {
            return "Audit: empty";
        }
        List<String> entries = new ArrayList<>();
        for (AdminAuditEntryView entry : audit) {
            if (entries.size() >= 10) {
                break;
            }
            entries.add(auditEntry(entry));
        }
        return entries.isEmpty() ? "Audit: empty" : "Audit: " + String.join(" | ", entries);
    }

    private String eventEntry(AdminEventView event) {
        Map<String, String> fields = event.fields();
        String islandId = field(fields, "islandId");
        String ticketId = field(fields, "ticketId");
        String playerUuid = field(fields, "playerUuid");
        String action = field(fields, "action");
        String reason = field(fields, "reason");
        String requestedNode = field(fields, "requestedNode");
        String clearedSession = field(fields, "clearedSession");
        String clearedTicket = field(fields, "clearedTicket");
        String nodeId = field(fields, "nodeId");
        if (nodeId.isBlank()) {
            nodeId = field(fields, "targetNode");
        }
        return (event.type().isBlank() ? "UNKNOWN_EVENT" : event.type())
            + (islandId.isBlank() ? "" : " 섬=" + islandId)
            + (ticketId.isBlank() ? "" : " ticket=" + shortId(ticketId))
            + (playerUuid.isBlank() ? "" : " player=" + shortId(playerUuid))
            + (action.isBlank() ? "" : " action=" + action)
            + (reason.isBlank() ? "" : " reason=" + reason)
            + routePrivacy.routeRequestedNodeSuffix(requestedNode)
            + (clearedSession.isBlank() ? "" : " session=" + clearedSession)
            + (clearedTicket.isBlank() ? "" : " ticketCleared=" + clearedTicket)
            + routePrivacy.routeNodeSuffix(nodeId)
            + (event.occurredAt().isBlank() ? "" : " at=" + event.occurredAt());
    }

    private String auditEntry(AdminAuditEntryView entry) {
        return (entry.action().isBlank() ? "UNKNOWN_ACTION" : entry.action())
            + (entry.targetType().isBlank() && entry.targetId().isBlank() ? "" : " target=" + entry.targetType() + ":" + entry.targetId())
            + (entry.actorType().isBlank() ? "" : " actor=" + entry.actorType())
            + (entry.createdAt().isBlank() ? "" : " at=" + entry.createdAt());
    }

    private static String field(Map<String, String> fields, String key) {
        if (fields == null) {
            return "";
        }
        String value = fields.get(key);
        return value == null ? "" : value;
    }

    private static String shortId(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
