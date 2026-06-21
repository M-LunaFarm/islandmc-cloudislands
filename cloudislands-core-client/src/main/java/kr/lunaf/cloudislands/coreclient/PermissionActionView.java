package kr.lunaf.cloudislands.coreclient;

public record PermissionActionView(boolean accepted, String code) {
    public PermissionActionView {
        code = code == null ? "" : code;
    }
}
