package kr.lunaf.cloudislands.storage;

public final class BundleRestorePolicy {
    public static final boolean MANIFEST_REQUIRED = true;
    public static final boolean PORTABLE_REQUIRED = true;
    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    public static final String COMPRESSION = "zstd";
    public static final String PLACEMENT_POLICY = "node-agnostic-shard-cell-remap";
    public static final String RESTORE_POLICY = "verify-checksum-then-restore-to-current-active-node";
    public static final String RESTORE_CHECKSUM_POLICY = "verify-manifest-checksum";
    public static final String SUPPORTED_FORMATS = "checksum=SHA-256,compression=zstd";
    public static final String ROLLBACK_POLICY = "lock-restoring-lobby-transfer-pre-restore-snapshot-restore-runtime-reset-reactivate";

    private BundleRestorePolicy() {
    }
}
