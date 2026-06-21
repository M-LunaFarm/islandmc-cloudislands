package kr.lunaf.cloudislands.coreclient;

public record RuntimeActionView(boolean accepted, String code) {
    public RuntimeActionView {
        code = code == null ? "" : code;
    }
}
