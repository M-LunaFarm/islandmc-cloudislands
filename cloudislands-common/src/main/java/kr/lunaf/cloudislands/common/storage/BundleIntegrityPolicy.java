package kr.lunaf.cloudislands.common.storage;

import java.util.Set;

public final class BundleIntegrityPolicy {
    public static final String CONTRACT = "portable-island-bundle-restore-requires-manifest-checksums-and-safe-tar-zstd";
    public static final String ARCHIVE_FORMAT = "tar.zst";
    public static final String COMPRESSION = "zstd";
    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    public static final String MANIFEST_FILE = "manifest.json";
    public static final String CHECKSUM_FILE = "checksums.sha256";
    public static final String CHUNKS_DIRECTORY = "chunks";
    public static final String ENTITIES_DIRECTORY = "entities";
    public static final String BLOCK_ENTITIES_DIRECTORY = "block-entities";
    public static final String CHECKSUM_COVERAGE_POLICY = "every-restored-regular-file-except-checksums-sha256-must-be-listed";
    public static final String RESTORE_SAFETY_POLICY = "reject-path-escape-absolute-path-windows-drive-symlink-and-unsupported-entry";
    public static final String RESTORE_GATE_POLICY = "restore-is-allowed-only-after-manifest-required-directories-and-checksums-pass";
    public static final String QUARANTINE_FALLBACK_POLICY = "failed-or-unverified-bundle-restore-keeps-island-quarantined";
    public static final Set<String> REQUIRED_ROOT_ENTRIES = Set.of(
        MANIFEST_FILE,
        CHECKSUM_FILE,
        CHUNKS_DIRECTORY,
        ENTITIES_DIRECTORY,
        BLOCK_ENTITIES_DIRECTORY
    );

    private BundleIntegrityPolicy() {
    }

    public static boolean requiredRootEntry(String entry) {
        return entry != null && REQUIRED_ROOT_ENTRIES.contains(entry);
    }

    public static boolean checksumProtectedFile(String relativeName) {
        return relativeName != null
            && !relativeName.isBlank()
            && !CHECKSUM_FILE.equals(relativeName.replace('\\', '/'));
    }

    public static String restoreContractSummary() {
        return CONTRACT + "," + RESTORE_GATE_POLICY + "," + QUARANTINE_FALLBACK_POLICY;
    }
}
