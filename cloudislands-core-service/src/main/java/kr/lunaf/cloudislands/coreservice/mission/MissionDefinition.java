package kr.lunaf.cloudislands.coreservice.mission;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public record MissionDefinition(String providerId, String missionKey, String kind, String category, String title, String description, String triggerType, String targetKey, long goal, String rewardType, String reward, boolean repeatable, boolean dailyReset, boolean enabled) {
    public MissionDefinition(String missionKey, String kind, String title, long goal, String reward) {
        this("cloudislands", missionKey, kind, "", title, "", "", "", goal, "", reward, false, false, true);
    }

    public MissionDefinition(String missionKey, String kind, String category, String title, String description, String triggerType, String targetKey, long goal, String rewardType, String reward, boolean repeatable, boolean dailyReset) {
        this("cloudislands", missionKey, kind, category, title, description, triggerType, targetKey, goal, rewardType, reward, repeatable, dailyReset, true);
    }

    public MissionDefinition(MissionProviderDefinitionSnapshot snapshot) {
        this(snapshot.providerId(), snapshot.missionKey(), snapshot.kind(), snapshot.category(), snapshot.title(), snapshot.description(), snapshot.triggerType(), snapshot.targetKey(), snapshot.goal(), snapshot.rewardType(), snapshot.reward(), snapshot.repeatable(), snapshot.dailyReset(), snapshot.enabled());
    }

    public MissionDefinition {
        providerId = providerId == null || providerId.isBlank() ? "cloudislands" : providerId.trim();
        missionKey = missionKey == null ? "" : missionKey.trim().toLowerCase();
        kind = MissionCatalog.normalizeKind(kind);
        category = category == null || category.isBlank() ? (kind.equals("CHALLENGE") ? "daily" : "general") : category.trim().toLowerCase();
        title = title == null || title.isBlank() ? missionKey : title.trim();
        description = description == null ? "" : description.trim();
        triggerType = triggerType == null ? "" : triggerType.trim().toUpperCase();
        targetKey = targetKey == null ? "" : targetKey.trim().toLowerCase();
        goal = Math.max(1L, goal);
        rewardType = rewardType == null || rewardType.isBlank() ? (reward == null || reward.isBlank() ? "" : "TEXT") : rewardType.trim().toUpperCase();
        reward = reward == null ? "" : reward.trim();
        repeatable = repeatable || kind.equals("CHALLENGE");
        dailyReset = dailyReset || kind.equals("CHALLENGE");
    }

    public IslandMissionSnapshot snapshot(UUID islandId, long progress, boolean completed, java.time.Instant updatedAt) {
        return new IslandMissionSnapshot(islandId, missionKey, kind, category, title, description, triggerType, targetKey, progress, goal, completed, rewardType, reward, repeatable, dailyReset, updatedAt);
    }
}
