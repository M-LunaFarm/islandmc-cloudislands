package kr.lunaf.cloudislands.storage.snapshot;

import java.util.List;

public final class SnapshotOperationPolicy {
    public static final String AUTOMATIC_TRIGGER_POLICY = "create,deactivate,periodic,before-delete,before-reset,before-migration,manual-admin";
    public static final String RETENTION_POLICY = "hourly-daily-weekly-manual-with-compression-and-checksum";
    public static final String ACTIVE_ROLLBACK_POLICY = "lock-restoring-evacuate-players-pre-restore-snapshot-promote-target-clear-runtime-reactivate-unlock";
    public static final String ACTIVE_ISLAND_PLAYER_POLICY = "move-active-island-players-to-lobby-before-promoting-snapshot";
    public static final String CHECKSUM_POLICY = "sha256-required-before-promote";
    public static final String PORTABILITY_POLICY = "portable-bundle-required-before-promote";

    private static final List<String> AUTOMATIC_TRIGGER_REASONS = List.of(
        "CREATED",
        "DEACTIVATION",
        "PERIODIC",
        "BEFORE_DELETE",
        "BEFORE_RESET",
        "BEFORE_MIGRATION",
        "MANUAL"
    );

    private static final List<String> ROLLBACK_STEPS = List.of(
        "LOCK_RESTORING",
        "EVACUATE_ACTIVE_PLAYERS_TO_LOBBY",
        "WRITE_PRE_RESTORE_SNAPSHOT",
        "PROMOTE_TARGET_SNAPSHOT",
        "CLEAR_ISLAND_RUNTIME",
        "REACTIVATE_ISLAND",
        "UNLOCK_ISLAND"
    );

    private SnapshotOperationPolicy() {}

    public static List<String> automaticTriggerReasons() {
        return AUTOMATIC_TRIGGER_REASONS;
    }

    public static String automaticTriggerReasonSummary() {
        return String.join(",", AUTOMATIC_TRIGGER_REASONS);
    }

    public static List<String> rollbackSteps() {
        return ROLLBACK_STEPS;
    }

    public static String rollbackStepSummary() {
        return String.join(">", ROLLBACK_STEPS);
    }

    public static String retentionSummary(SnapshotRetentionPolicy retentionPolicy) {
        SnapshotRetentionPolicy policy = retentionPolicy == null
            ? SnapshotRetentionPolicy.defaultPolicy()
            : retentionPolicy.normalized();
        return "hourly=" + policy.keepHourly()
            + ",daily=" + policy.keepDaily()
            + ",weekly=" + policy.keepWeekly()
            + ",manual=" + policy.keepManual()
            + ",compress=" + policy.compress()
            + ",checksum=" + policy.checksumAlgorithm()
            + ",automatic=" + policy.retainedAutomaticSnapshotCount()
            + ",manualRetained=" + policy.retainedManualSnapshotCount()
            + ",total=" + policy.retainedSnapshotCount();
    }
}
