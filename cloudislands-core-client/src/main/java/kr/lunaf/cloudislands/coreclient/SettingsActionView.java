package kr.lunaf.cloudislands.coreclient;

public record SettingsActionView(boolean accepted, String code) {
    public SettingsActionView {
        code = code == null ? "" : code;
    }
}
