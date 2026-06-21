package kr.lunaf.cloudislands.coreclient;

public record MemberActionView(boolean accepted, String code, String expiresAt) {
    public MemberActionView {
        code = code == null ? "" : code;
        expiresAt = expiresAt == null ? "" : expiresAt;
    }
}
