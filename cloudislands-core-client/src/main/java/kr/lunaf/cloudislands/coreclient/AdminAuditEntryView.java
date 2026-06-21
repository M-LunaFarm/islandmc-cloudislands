package kr.lunaf.cloudislands.coreclient;

import java.util.Map;

public record AdminAuditEntryView(String id, String actorUuid, String actorType, String action, String targetType, String targetId, Map<String, String> payload, String createdAt) {
    public AdminAuditEntryView {
        id = id == null ? "" : id;
        actorUuid = actorUuid == null ? "" : actorUuid;
        actorType = actorType == null ? "" : actorType;
        action = action == null ? "" : action;
        targetType = targetType == null ? "" : targetType;
        targetId = targetId == null ? "" : targetId;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        createdAt = createdAt == null ? "" : createdAt;
    }
}
