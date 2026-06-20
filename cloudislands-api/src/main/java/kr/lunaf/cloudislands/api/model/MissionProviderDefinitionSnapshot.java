package kr.lunaf.cloudislands.api.model;

import java.time.Instant;

public record MissionProviderDefinitionSnapshot(
    String providerId,
    String missionKey,
    String kind,
    String title,
    long goal,
    String reward,
    boolean enabled,
    Instant updatedAt
) {
    public MissionProviderDefinitionSnapshot {
        providerId = safe(providerId, "cloudislands");
        missionKey = safe(missionKey, "").toLowerCase();
        kind = normalizeKind(kind);
        title = safe(title, missionKey);
        goal = Math.max(1L, goal);
        reward = safe(reward, "");
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public MissionProviderDefinitionSnapshot(String providerId, String missionKey, String kind, String title, long goal, String reward) {
        this(providerId, missionKey, kind, title, goal, reward, true, Instant.EPOCH);
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String normalizeKind(String value) {
        String normalized = safe(value, "MISSION").toUpperCase();
        return normalized.equals("CHALLENGE") ? "CHALLENGE" : "MISSION";
    }
}
