package kr.lunaf.cloudislands.api.model;

import java.time.Instant;

public record MissionProviderDefinitionSnapshot(
    String providerId,
    String missionKey,
    String kind,
    String category,
    String title,
    String description,
    String triggerType,
    String targetKey,
    long goal,
    String rewardType,
    String reward,
    boolean repeatable,
    boolean dailyReset,
    boolean enabled,
    Instant updatedAt
) {
    public MissionProviderDefinitionSnapshot {
        providerId = safe(providerId, "cloudislands");
        missionKey = safe(missionKey, "").toLowerCase();
        kind = normalizeKind(kind);
        category = safe(category, kind.equals("CHALLENGE") ? "daily" : "general").toLowerCase();
        title = safe(title, missionKey);
        description = safe(description, "");
        triggerType = safe(triggerType, "").toUpperCase();
        targetKey = safe(targetKey, "").toLowerCase();
        goal = Math.max(1L, goal);
        rewardType = safe(rewardType, reward == null || reward.isBlank() ? "" : "TEXT").toUpperCase();
        reward = safe(reward, "");
        repeatable = repeatable || kind.equals("CHALLENGE");
        dailyReset = dailyReset || kind.equals("CHALLENGE");
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public MissionProviderDefinitionSnapshot(String providerId, String missionKey, String kind, String title, long goal, String reward, boolean enabled, Instant updatedAt) {
        this(providerId, missionKey, kind, "", title, "", "", "", goal, "", reward, false, false, enabled, updatedAt);
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
