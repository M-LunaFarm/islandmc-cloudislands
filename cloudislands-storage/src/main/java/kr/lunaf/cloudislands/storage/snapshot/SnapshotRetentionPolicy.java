package kr.lunaf.cloudislands.storage.snapshot;

public record SnapshotRetentionPolicy(int keepHourly, int keepDaily, int keepWeekly, int keepManual, boolean compress, String checksumAlgorithm) {
    public static SnapshotRetentionPolicy defaultPolicy() {
        return new SnapshotRetentionPolicy(24, 7, 4, 50, true, "SHA-256");
    }

    public int retainedSnapshotCount() {
        return Math.max(1, keepHourly)
            + Math.max(0, keepDaily)
            + Math.max(0, keepWeekly)
            + Math.max(0, keepManual);
    }
}
