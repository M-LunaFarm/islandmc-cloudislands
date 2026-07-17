package kr.lunaf.cloudislands.coreclient;

public record ReviewActionView(boolean accepted, String code, String moderationState, int reportCount) {
    public ReviewActionView(boolean accepted, String code) {
        this(accepted, code, "", 0);
    }

    public ReviewActionView {
        code = code == null ? "" : code;
        moderationState = moderationState == null ? "" : moderationState;
        reportCount = Math.max(0, reportCount);
    }
}
