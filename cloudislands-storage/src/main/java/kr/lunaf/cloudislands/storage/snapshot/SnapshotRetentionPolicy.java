package kr.lunaf.cloudislands.storage.snapshot;

public record SnapshotRetentionPolicy(int keepHourly, int keepDaily, int keepWeekly, int keepManual, boolean compress, String checksumAlgorithm) {
    public static SnapshotRetentionPolicy defaultPolicy() {
        return new SnapshotRetentionPolicy(24, 7, 4, 50, true, "SHA-256");
    }

    public SnapshotRetentionPolicy normalized() {
        String algorithm = checksumAlgorithm == null || checksumAlgorithm.isBlank() ? "SHA-256" : checksumAlgorithm.trim().toUpperCase(java.util.Locale.ROOT);
        return new SnapshotRetentionPolicy(
            Math.max(1, keepHourly),
            Math.max(0, keepDaily),
            Math.max(0, keepWeekly),
            Math.max(0, keepManual),
            compress,
            algorithm
        );
    }

    public int retainedAutomaticSnapshotCount() {
        SnapshotRetentionPolicy normalized = normalized();
        return safeAdd(normalized.keepHourly, normalized.keepDaily, normalized.keepWeekly);
    }

    public int retainedManualSnapshotCount() {
        return normalized().keepManual;
    }

    public int retainedSnapshotCount() {
        SnapshotRetentionPolicy normalized = normalized();
        return safeAdd(normalized.keepHourly, normalized.keepDaily, normalized.keepWeekly, normalized.keepManual);
    }

    private static int safeAdd(int... values) {
        long sum = 0L;
        for (int value : values) {
            sum += Math.max(0, value);
        }
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(1, (int) sum);
    }
}
