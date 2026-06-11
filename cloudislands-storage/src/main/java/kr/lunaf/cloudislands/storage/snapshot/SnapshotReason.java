package kr.lunaf.cloudislands.storage.snapshot;

public enum SnapshotReason {
    CREATED,
    DEACTIVATION,
    PERIODIC,
    BEFORE_DELETE,
    BEFORE_RESET,
    BEFORE_MIGRATION,
    MANUAL,
    BEFORE_RESTORE
}
