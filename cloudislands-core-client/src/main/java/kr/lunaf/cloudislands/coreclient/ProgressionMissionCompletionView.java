package kr.lunaf.cloudislands.coreclient;

public record ProgressionMissionCompletionView(boolean accepted, String code, String missionKey, String title, String reward) {
    public ProgressionMissionCompletionView {
        code = code == null ? "" : code;
        missionKey = missionKey == null ? "" : missionKey;
        title = title == null ? "" : title;
        reward = reward == null ? "" : reward;
    }
}
