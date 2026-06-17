package kr.lunaf.cloudislands.common.storage;

public final class StorageOutagePolicy {
    public static final String CONTRACT = "object-storage-outage-keeps-active-islands-local-and-queues-save-retry";
    public static final String ACTIVE_ISLAND_POLICY = "active islands stay loaded on the current Paper node while object storage is degraded";
    public static final String NEW_ACTIVATION_POLICY = "new activation snapshot restore and recovery are blocked until storage is available";
    public static final String SAVE_RETRY_POLICY = "periodic and empty-island save failures remain queued for retry";
    public static final String DEACTIVATION_POLICY = "empty island deactivation waits for a successful save before local unload";
    public static final String OBSERVABILITY_KEYS = "storageSaveRetryQueueTotal,periodicSaveRetryQueue,emptySaveRetryQueue,storageOperationFailuresTotal";

    private StorageOutagePolicy() {
    }
}
