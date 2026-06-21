package kr.lunaf.cloudislands.coreclient;

public record ChatActionView(boolean accepted, String code) {
    public ChatActionView {
        code = code == null ? "" : code;
    }
}
