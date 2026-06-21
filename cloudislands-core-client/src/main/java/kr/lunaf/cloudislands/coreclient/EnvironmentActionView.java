package kr.lunaf.cloudislands.coreclient;

public record EnvironmentActionView(boolean accepted, String code, String key, long value) {
    public EnvironmentActionView {
        code = code == null ? "" : code;
        key = key == null ? "" : key;
    }
}
