package kr.lunaf.cloudislands.migration.superior;

public enum MigrationRunState {
    SCANNED,
    DRY_RUN_FAILED,
    DRY_RUN_PASSED,
    IMPORTING,
    IMPORTED,
    VERIFYING,
    VERIFIED,
    ROLLED_BACK
}
