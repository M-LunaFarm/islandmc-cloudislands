package kr.lunaf.cloudislands.api.model;

public record NodeLevelScanSnapshot(boolean running, String lastIsland, long startedAt, long finishedAt, long failedAt) {
    public static NodeLevelScanSnapshot empty() {
        return new NodeLevelScanSnapshot(false, "", 0L, 0L, 0L);
    }
}
