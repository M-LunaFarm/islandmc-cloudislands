package kr.lunaf.cloudislands.coreclient;

public record ProgressionMissionCompletionView(boolean accepted, String code, String islandId, String missionKey, String kind, String title, long progress, long goal, boolean completed, String reward, String updatedAt) {
    public ProgressionMissionCompletionView(boolean accepted, String code, String missionKey, String title, String reward) {
        this(accepted, code, "", missionKey, "", title, 0L, 0L, false, reward, "");
    }

    public ProgressionMissionCompletionView {
        code = code == null ? "" : code;
        islandId = islandId == null ? "" : islandId;
        missionKey = missionKey == null ? "" : missionKey;
        kind = kind == null ? "" : kind;
        title = title == null ? "" : title;
        reward = reward == null ? "" : reward;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
