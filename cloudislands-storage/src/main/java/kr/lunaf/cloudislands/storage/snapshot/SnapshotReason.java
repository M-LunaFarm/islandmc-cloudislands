package kr.lunaf.cloudislands.storage.snapshot;

import java.util.Set;

public enum SnapshotReason {
    CREATED,
    DEACTIVATION,
    PERIODIC,
    BEFORE_DELETE,
    BEFORE_RESET,
    BEFORE_MIGRATION,
    MANUAL,
    BEFORE_RESTORE;

    private static final Set<SnapshotReason> REQUIRED_SNAPSHOT_TRIGGERS = Set.of(
        CREATED,
        DEACTIVATION,
        PERIODIC,
        BEFORE_DELETE,
        BEFORE_RESET,
        BEFORE_MIGRATION,
        MANUAL
    );

    public static Set<SnapshotReason> requiredSnapshotTriggers() {
        return REQUIRED_SNAPSHOT_TRIGGERS;
    }

    public static SnapshotReason preRestoreSnapshotReason() {
        return BEFORE_RESTORE;
    }

    public boolean requiredTrigger() {
        return REQUIRED_SNAPSHOT_TRIGGERS.contains(this);
    }

    public boolean manualRetentionBucket() {
        return this == MANUAL;
    }
}
