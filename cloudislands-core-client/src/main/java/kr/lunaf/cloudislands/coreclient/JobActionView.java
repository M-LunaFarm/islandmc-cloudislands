package kr.lunaf.cloudislands.coreclient;

public record JobActionView(boolean accepted, String code) {
    public JobActionView {
        code = code == null ? "" : code;
    }
}
