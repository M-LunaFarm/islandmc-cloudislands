package kr.lunaf.cloudislands.common.storage;

import java.util.Set;

public final class StorageOutagePolicy {
    public static final String CONTRACT = "object-storage-outage-keeps-active-islands-local-and-queues-save-retry";
    public static final String ACTIVE_ISLAND_POLICY = "active islands stay loaded on the current Paper node while object storage is degraded";
    public static final String NEW_ACTIVATION_POLICY = "new activation snapshot restore and recovery are blocked until storage is available";
    public static final String STORAGE_BLOCK_CODE = "STORAGE_UNAVAILABLE";
    public static final String RESTRICTED_OPERATION_POLICY = "new activation, island save, island snapshot, and island recovery are restricted while object storage is unavailable";
    public static final String SAVE_RETRY_POLICY = "periodic and empty-island save failures remain queued for retry";
    public static final String DEACTIVATION_POLICY = "empty island deactivation waits for a successful save before local unload";
    public static final String LOCAL_FALLBACK_POLICY = "local-filesystem-fallback-stores-portable-bundles-until-object-storage-recovers";
    public static final String RECOVERY_RECONCILE_POLICY = "object-storage-recovery-reuploads-local-fallback-bundles-after-manifest-and-checksum-verification";
    public static final String SPLIT_BRAIN_PREVENTION_POLICY = "local-fallback-bundles-are-node-temporary-and-never-become-shared-authority";
    public static final String OBSERVABILITY_KEYS = "storageSaveRetryQueueTotal,periodicSaveRetryQueue,emptySaveRetryQueue,storageOperationFailuresTotal,localFallbackBundleQueueTotal,storageFallbackReconcileFailuresTotal";
    public static final Set<String> ALLOWED_OPERATIONS = Set.of(
        "active-island-play"
    );
    public static final Set<String> RESTRICTED_OPERATIONS = Set.of(
        "new-activation",
        "island-save",
        "island-snapshot",
        "island-recovery"
    );
    public static final Set<String> RETRY_QUEUES = Set.of(
        "periodic-save",
        "empty-island-save",
        "local-fallback-bundle-reconcile"
    );

    private StorageOutagePolicy() {
    }

    public static boolean allowedDuringOutage(String operation) {
        return operation != null && ALLOWED_OPERATIONS.contains(operation);
    }

    public static boolean restrictedDuringOutage(String operation) {
        return operation != null && RESTRICTED_OPERATIONS.contains(operation);
    }

    public static boolean retryQueue(String queue) {
        return queue != null && RETRY_QUEUES.contains(queue);
    }

    public static boolean localFallbackQueue(String queue) {
        return "local-fallback-bundle-reconcile".equals(queue);
    }
}
