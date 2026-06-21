package kr.lunaf.cloudislands.coreclient;

import java.util.LinkedHashMap;
import java.util.Map;

public record JobView(String id, String type, String islandId, String targetNode, String state, int priority, long attempts, String lockedBy, String error, Map<String, String> payload, String createdAt, String updatedAt) {
    public JobView {
        id = id == null ? "" : id;
        type = type == null ? "" : type;
        islandId = islandId == null ? "" : islandId;
        state = state == null ? "" : state;
        targetNode = targetNode == null ? "" : targetNode;
        lockedBy = lockedBy == null ? "" : lockedBy;
        error = error == null ? "" : error;
        payload = normalizePayload(payload);
        createdAt = createdAt == null ? "" : createdAt;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }

    private static Map<String, String> normalizePayload(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            safe.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue());
        }
        return safe.isEmpty() ? Map.of() : Map.copyOf(safe);
    }
}
