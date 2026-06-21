package kr.lunaf.cloudislands.coreclient;

public record RouteClearView(boolean accepted, String code) {
    public RouteClearView {
        code = code == null ? "" : code;
    }
}
