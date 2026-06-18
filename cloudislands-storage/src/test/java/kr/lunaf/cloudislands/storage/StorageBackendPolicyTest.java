package kr.lunaf.cloudislands.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StorageBackendPolicyTest {
    @Test
    void pinsStorageSetupFieldsAndFallbackOrder() {
        assertEquals("s3-or-minio-primary-object-storage-with-local-filesystem-fallback", StorageBackendPolicy.CONTRACT);
        assertEquals("setup.storage", StorageBackendPolicy.CONFIG_PATH);
        assertEquals("setup.storage.type", StorageBackendPolicy.SELECTED_BACKEND_FIELD);
        assertEquals("setup.storage.fallback.enabled", StorageBackendPolicy.FALLBACK_ENABLED_FIELD);
        assertEquals("setup.storage.fallback.local-path", StorageBackendPolicy.FALLBACK_PATH_FIELD);
        assertEquals(List.of("S3", "MINIO", "LOCAL_FILESYSTEM"), StorageBackendPolicy.SAFE_FALLBACK_ORDER);
    }

    @Test
    void treatsS3AndMinioAsSharedObjectStorageBackends() {
        assertEquals(Set.of("S3", "MINIO"), StorageBackendPolicy.SHARED_BACKENDS);
        assertTrue(StorageBackendPolicy.supportedBackend("s3"));
        assertTrue(StorageBackendPolicy.supportedBackend("minio"));
        assertTrue(StorageBackendPolicy.sharedBackend("object-storage"));
        assertTrue(StorageBackendPolicy.safeForMultiNode("MINIO"));
        assertFalse(StorageBackendPolicy.sharedBackend("local-filesystem"));
        assertFalse(StorageBackendPolicy.safeForMultiNode("local"));
    }

    @Test
    void keepsLocalFilesystemAsExplicitFallbackOnly() {
        assertTrue(StorageBackendPolicy.localFallbackBackend("local"));
        assertTrue(StorageBackendPolicy.localFallbackBackend("local-fs"));
        assertTrue(StorageBackendPolicy.localFallbackBackend("filesystem"));
        assertEquals(
            "LOCAL_FILESYSTEM-is-single-node-or-emergency-fallback-not-shared-production-authority",
            StorageBackendPolicy.LOCAL_FALLBACK_POLICY
        );
        assertEquals("local-filesystem-fallback-not-multi-node-authority", StorageBackendPolicy.fallbackReason("local-filesystem"));
    }

    @Test
    void fallsUnknownOrEmptyStorageBackendBackToS3() {
        assertEquals("S3", StorageBackendPolicy.fallbackTarget(""));
        assertEquals("S3", StorageBackendPolicy.fallbackTarget("unknown"));
        assertEquals("MINIO", StorageBackendPolicy.fallbackTarget("minio"));
        assertEquals("storage-backend-empty-use-s3", StorageBackendPolicy.fallbackReason(""));
        assertEquals("storage-backend-unknown-use-s3", StorageBackendPolicy.fallbackReason("ftp"));
        assertEquals("storage-backend-shared-object-storage", StorageBackendPolicy.fallbackReason("s3"));
    }

    @Test
    void keepsPortableBundleContractLinkedToRestorePolicy() {
        assertEquals(
            "portable-bundle-manifest-checksum-compression-restore-preflight-required",
            StorageBackendPolicy.BUNDLE_CONTRACT
        );
        assertEquals("SHA-256", BundleRestorePolicy.CHECKSUM_ALGORITHM);
        assertEquals("zstd", BundleRestorePolicy.COMPRESSION);
    }
}
