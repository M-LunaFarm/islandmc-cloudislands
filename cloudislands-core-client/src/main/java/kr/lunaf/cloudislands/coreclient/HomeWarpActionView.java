package kr.lunaf.cloudislands.coreclient;

public record HomeWarpActionView(boolean accepted, String code) {
    public HomeWarpActionView {
        code = code == null ? "" : code;
    }
}
