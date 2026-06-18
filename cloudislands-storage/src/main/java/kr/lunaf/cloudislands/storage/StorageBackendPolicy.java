package kr.lunaf.cloudislands.storage;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class StorageBackendPolicy {
    public static final String CONTRACT = "s3-or-minio-primary-object-storage-with-local-filesystem-fallback";
    public static final String CONFIG_PATH = "setup.storage";
    public static final String SELECTED_BACKEND_FIELD = "setup.storage.type";
    public static final String FALLBACK_ENABLED_FIELD = "setup.storage.fallback.enabled";
    public static final String FALLBACK_PATH_FIELD = "setup.storage.fallback.local-path";
    public static final String PRIMARY_BACKEND_POLICY = "S3-and-MINIO-are-shared-object-storage-backends-for-multi-island-node-pools";
    public static final String LOCAL_FALLBACK_POLICY = "LOCAL_FILESYSTEM-is-single-node-or-emergency-fallback-not-shared-production-authority";
    public static final String BUNDLE_CONTRACT = "portable-bundle-manifest-checksum-compression-restore-preflight-required";

    public static final List<String> SUPPORTED_BACKENDS = List.of(
        "S3",
        "MINIO",
        "LOCAL_FILESYSTEM"
    );

    public static final Set<String> SHARED_BACKENDS = Set.of(
        "S3",
        "MINIO"
    );

    public static final List<String> SAFE_FALLBACK_ORDER = List.of(
        "S3",
        "MINIO",
        "LOCAL_FILESYSTEM"
    );

    private StorageBackendPolicy() {
    }

    public static String normalizeBackend(String backend) {
        if (backend == null || backend.isBlank()) {
            return "";
        }
        String normalized = backend.trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);
        if (normalized.equals("LOCAL") || normalized.equals("FILESYSTEM") || normalized.equals("LOCAL_FS")) {
            return "LOCAL_FILESYSTEM";
        }
        if (normalized.equals("OBJECT_STORAGE")) {
            return "S3";
        }
        return normalized;
    }

    public static boolean supportedBackend(String backend) {
        return SUPPORTED_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean sharedBackend(String backend) {
        return SHARED_BACKENDS.contains(normalizeBackend(backend));
    }

    public static boolean localFallbackBackend(String backend) {
        return "LOCAL_FILESYSTEM".equals(normalizeBackend(backend));
    }

    public static String fallbackTarget(String requestedBackend) {
        String normalized = normalizeBackend(requestedBackend);
        if (supportedBackend(normalized)) {
            return normalized;
        }
        return "S3";
    }

    public static boolean safeForMultiNode(String backend) {
        return sharedBackend(backend);
    }

    public static String fallbackReason(String requestedBackend) {
        String normalized = normalizeBackend(requestedBackend);
        if (normalized.isBlank()) {
            return "storage-backend-empty-use-s3";
        }
        if (sharedBackend(normalized)) {
            return "storage-backend-shared-object-storage";
        }
        if (localFallbackBackend(normalized)) {
            return "local-filesystem-fallback-not-multi-node-authority";
        }
        return "storage-backend-unknown-use-s3";
    }
}
