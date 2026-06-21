package kr.lunaf.cloudislands.coreclient;

public record EnvironmentActionView(boolean accepted, String code, String key, long value, String islandId, String updatedBy, String updatedAt) {
    public EnvironmentActionView(boolean accepted, String code, String key, long value) {
        this(accepted, code, key, value, "", "", "");
    }

    public EnvironmentActionView {
        code = code == null ? "" : code;
        key = key == null ? "" : key;
        islandId = islandId == null ? "" : islandId;
        updatedBy = updatedBy == null ? "" : updatedBy;
        updatedAt = updatedAt == null ? "" : updatedAt;
    }
}
