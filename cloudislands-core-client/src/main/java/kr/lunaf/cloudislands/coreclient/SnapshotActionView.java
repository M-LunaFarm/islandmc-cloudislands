package kr.lunaf.cloudislands.coreclient;

public record SnapshotActionView(boolean accepted, String code) {
    public SnapshotActionView {
        code = code == null ? "" : code;
    }
}
