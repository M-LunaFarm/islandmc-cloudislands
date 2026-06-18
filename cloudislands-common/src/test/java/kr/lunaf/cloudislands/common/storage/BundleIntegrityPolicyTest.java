package kr.lunaf.cloudislands.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BundleIntegrityPolicyTest {
    @Test
    void pinsPortableBundleArchiveContract() {
        assertEquals(
            "portable-island-bundle-restore-requires-manifest-checksums-and-safe-tar-zstd",
            BundleIntegrityPolicy.CONTRACT
        );
        assertEquals("tar.zst", BundleIntegrityPolicy.ARCHIVE_FORMAT);
        assertEquals("zstd", BundleIntegrityPolicy.COMPRESSION);
        assertEquals("SHA-256", BundleIntegrityPolicy.CHECKSUM_ALGORITHM);
    }

    @Test
    void requiresManifestChecksumAndWorldPayloadDirectories() {
        assertTrue(BundleIntegrityPolicy.requiredRootEntry("manifest.json"));
        assertTrue(BundleIntegrityPolicy.requiredRootEntry("checksums.sha256"));
        assertTrue(BundleIntegrityPolicy.requiredRootEntry("chunks"));
        assertTrue(BundleIntegrityPolicy.requiredRootEntry("entities"));
        assertTrue(BundleIntegrityPolicy.requiredRootEntry("block-entities"));
        assertFalse(BundleIntegrityPolicy.requiredRootEntry("region"));
        assertFalse(BundleIntegrityPolicy.requiredRootEntry(null));
    }

    @Test
    void protectsEveryPayloadFileExceptChecksumListItself() {
        assertEquals(
            "every-restored-regular-file-except-checksums-sha256-must-be-listed",
            BundleIntegrityPolicy.CHECKSUM_COVERAGE_POLICY
        );
        assertTrue(BundleIntegrityPolicy.checksumProtectedFile("manifest.json"));
        assertTrue(BundleIntegrityPolicy.checksumProtectedFile("chunks/r.0.0.mca"));
        assertFalse(BundleIntegrityPolicy.checksumProtectedFile("checksums.sha256"));
        assertFalse(BundleIntegrityPolicy.checksumProtectedFile(""));
        assertFalse(BundleIntegrityPolicy.checksumProtectedFile(null));
    }

    @Test
    void describesRestoreGateAndQuarantineFallback() {
        assertEquals(
            "reject-path-escape-absolute-path-windows-drive-symlink-and-unsupported-entry",
            BundleIntegrityPolicy.RESTORE_SAFETY_POLICY
        );
        assertEquals(
            "restore-is-allowed-only-after-manifest-required-directories-and-checksums-pass",
            BundleIntegrityPolicy.RESTORE_GATE_POLICY
        );
        assertEquals(
            "failed-or-unverified-bundle-restore-keeps-island-quarantined",
            BundleIntegrityPolicy.QUARANTINE_FALLBACK_POLICY
        );
        assertEquals(
            "portable-island-bundle-restore-requires-manifest-checksums-and-safe-tar-zstd,restore-is-allowed-only-after-manifest-required-directories-and-checksums-pass,failed-or-unverified-bundle-restore-keeps-island-quarantined",
            BundleIntegrityPolicy.restoreContractSummary()
        );
    }
}
