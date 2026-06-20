package kr.lunaf.cloudislands.coreservice.mission;

import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public record MissionDefinition(String providerId, String missionKey, String kind, String title, long goal, String reward, boolean enabled) {
    public MissionDefinition(String missionKey, String kind, String title, long goal, String reward) {
        this("cloudislands", missionKey, kind, title, goal, reward, true);
    }

    public MissionDefinition(MissionProviderDefinitionSnapshot snapshot) {
        this(snapshot.providerId(), snapshot.missionKey(), snapshot.kind(), snapshot.title(), snapshot.goal(), snapshot.reward(), snapshot.enabled());
    }

    public MissionDefinition {
        providerId = providerId == null || providerId.isBlank() ? "cloudislands" : providerId.trim();
        missionKey = missionKey == null ? "" : missionKey.trim().toLowerCase();
        kind = MissionCatalog.normalizeKind(kind);
        title = title == null || title.isBlank() ? missionKey : title.trim();
        goal = Math.max(1L, goal);
        reward = reward == null ? "" : reward.trim();
    }
}
