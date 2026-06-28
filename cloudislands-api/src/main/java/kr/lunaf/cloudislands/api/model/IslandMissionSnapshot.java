package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandMissionSnapshot(
    UUID islandId,
    String missionKey,
    String kind,
    String category,
    String title,
    String description,
    String triggerType,
    String targetKey,
    long progress,
    long goal,
    boolean completed,
    String rewardType,
    String reward,
    boolean repeatable,
    boolean dailyReset,
    Instant updatedAt
) {
    public IslandMissionSnapshot(UUID islandId, String missionKey, String kind, String title, long progress, long goal, boolean completed, String reward, Instant updatedAt) {
        this(islandId, missionKey, kind, "", title, "", "", "", progress, goal, completed, "", reward, false, false, updatedAt);
    }

    public IslandMissionSnapshot {
        missionKey = missionKey == null ? "" : missionKey.trim().toLowerCase();
        kind = kind == null || kind.isBlank() ? "MISSION" : kind.trim().toUpperCase();
        kind = kind.equals("CHALLENGE") ? "CHALLENGE" : "MISSION";
        category = category == null || category.isBlank() ? (kind.equals("CHALLENGE") ? "daily" : "general") : category.trim().toLowerCase();
        title = title == null || title.isBlank() ? missionKey : title.trim();
        description = description == null ? "" : description.trim();
        triggerType = triggerType == null ? "" : triggerType.trim().toUpperCase();
        targetKey = targetKey == null ? "" : targetKey.trim().toLowerCase();
        progress = Math.max(0L, progress);
        goal = Math.max(1L, goal);
        completed = completed || progress >= goal;
        rewardType = rewardType == null || rewardType.isBlank() ? (reward == null || reward.isBlank() ? "" : "TEXT") : rewardType.trim().toUpperCase();
        reward = reward == null ? "" : reward.trim();
        repeatable = repeatable || kind.equals("CHALLENGE");
        dailyReset = dailyReset || kind.equals("CHALLENGE");
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }
}
