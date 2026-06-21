package kr.lunaf.cloudislands.coreclient;

public record IslandLifecycleActionView(boolean accepted, String code, String islandId, long snapshotNo, String storagePath) {
    public IslandLifecycleActionView(boolean accepted, String code) {
        this(accepted, code, "", 0L, "");
    }

    public IslandLifecycleActionView {
        code = code == null ? "" : code;
        islandId = islandId == null ? "" : islandId;
        storagePath = storagePath == null ? "" : storagePath;
    }
}
