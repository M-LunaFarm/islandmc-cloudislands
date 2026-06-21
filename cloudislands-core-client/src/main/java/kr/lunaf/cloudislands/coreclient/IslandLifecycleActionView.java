package kr.lunaf.cloudislands.coreclient;

public record IslandLifecycleActionView(boolean accepted, String code) {
    public IslandLifecycleActionView {
        code = code == null ? "" : code;
    }
}
