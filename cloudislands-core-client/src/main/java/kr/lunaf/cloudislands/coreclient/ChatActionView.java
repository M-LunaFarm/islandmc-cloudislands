package kr.lunaf.cloudislands.coreclient;

public record ChatActionView(boolean accepted, String code, String channel, String message) {
    public ChatActionView(boolean accepted, String code) {
        this(accepted, code, "", "");
    }

    public ChatActionView {
        code = code == null ? "" : code;
        channel = channel == null ? "" : channel;
        message = message == null ? "" : message;
    }
}
