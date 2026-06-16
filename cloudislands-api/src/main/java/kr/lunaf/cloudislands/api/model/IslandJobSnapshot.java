package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record IslandJobSnapshot(
    UUID jobId,
    String type,
    UUID islandId,
    String targetNode,
    String state,
    int priority,
    int attempts,
    String lockedBy,
    String errorMessage,
    Map<String, String> payload,
    Instant createdAt,
    Instant updatedAt
) {
    public IslandJobSnapshot {
        payload = normalizePayload(payload);
    }

    private static Map<String, String> normalizePayload(Map<String, String> payload) {
        if (payload == null || payload.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safePayload = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            safePayload.put(key, entry.getValue() == null ? "" : entry.getValue());
        }
        return safePayload.isEmpty() ? Map.of() : Map.copyOf(safePayload);
    }
}
