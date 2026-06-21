package kr.lunaf.cloudislands.coreclient;

public record ReviewActionView(boolean accepted, String code) {
    public ReviewActionView {
        code = code == null ? "" : code;
    }
}
