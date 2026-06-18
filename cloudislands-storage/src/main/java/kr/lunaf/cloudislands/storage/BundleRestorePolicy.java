package kr.lunaf.cloudislands.storage;

public final class BundleRestorePolicy {
    public static final boolean MANIFEST_REQUIRED = true;
    public static final boolean PORTABLE_REQUIRED = true;
    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    public static final String COMPRESSION = "zstd";
    public static final String COMPRESSION_POLICY = "accept-zstd-bundles-only-until-codec-streaming-layer-is-added";
    public static final String PLACEMENT_POLICY = "node-agnostic-shard-cell-remap";
    public static final String NODE_BINDING_POLICY = "portable-manifest-must-not-store-active-node-server-name-or-channel";
    public static final String MANIFEST_FORBIDDEN_RUNTIME_FIELDS = "activeNode,activeServer,serverName,velocityServerName,nodeId,channelName";
    public static final String PORTABLE_BUNDLE_LAYOUT = "islands/{island_uuid}/manifest.json+latest+snapshots/{snapshot}/bundle.tar.zst+checksums.sha256";
    public static final String RESTORE_POLICY = "verify-checksum-then-restore-to-current-active-node";
    public static final String RESTORE_CHECKSUM_POLICY = "verify-manifest-checksum";
    public static final String RESTORE_PREFLIGHT_POLICY = "reject-restore-when-portable-checksum-compression-storage-or-size-metadata-is-missing";
    public static final String RESTORE_REQUIREMENTS = "portable=true,checksum,checksumAlgorithm=SHA-256,compression=zstd,storagePath,sizeBytes>0,manifest.json,checksums.sha256,nodeBinding=none";
    public static final String SUPPORTED_FORMATS = "checksum=SHA-256,compression=zstd,file=bundle.tar.zst";
    public static final String ROLLBACK_POLICY = "lock-restoring-lobby-transfer-pre-restore-snapshot-restore-runtime-reset-reactivate";

    private BundleRestorePolicy() {
    }
}
