package kr.lunaf.cloudislands.common.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StorageOutagePolicyTest {
    @Test
    void keepsActiveIslandPlayAvailableButLocalOnly() {
        assertEquals(
            "object-storage-outage-keeps-active-islands-local-and-queues-save-retry",
            StorageOutagePolicy.CONTRACT
        );
        assertEquals(
            "active islands stay loaded on the current Paper node while object storage is degraded",
            StorageOutagePolicy.ACTIVE_ISLAND_POLICY
        );
        assertTrue(StorageOutagePolicy.allowedDuringOutage("active-island-play"));
        assertFalse(StorageOutagePolicy.allowedDuringOutage("new-activation"));
        assertFalse(StorageOutagePolicy.allowedDuringOutage(null));
    }

    @Test
    void restrictsStorageBackedOperationsUntilStorageRecovers() {
        assertEquals("STORAGE_UNAVAILABLE", StorageOutagePolicy.STORAGE_BLOCK_CODE);
        assertEquals(
            "new activation, island save, island snapshot, and island recovery are restricted while object storage is unavailable",
            StorageOutagePolicy.RESTRICTED_OPERATION_POLICY
        );
        assertTrue(StorageOutagePolicy.restrictedDuringOutage("new-activation"));
        assertTrue(StorageOutagePolicy.restrictedDuringOutage("island-save"));
        assertTrue(StorageOutagePolicy.restrictedDuringOutage("island-snapshot"));
        assertTrue(StorageOutagePolicy.restrictedDuringOutage("island-recovery"));
        assertFalse(StorageOutagePolicy.restrictedDuringOutage("active-island-play"));
        assertFalse(StorageOutagePolicy.restrictedDuringOutage(null));
    }

    @Test
    void keepsFailedSavesQueuedForRetryBeforeUnload() {
        assertEquals(
            "periodic and empty-island save failures remain queued for retry",
            StorageOutagePolicy.SAVE_RETRY_POLICY
        );
        assertEquals(
            "empty island deactivation waits for a successful save before local unload",
            StorageOutagePolicy.DEACTIVATION_POLICY
        );
        assertTrue(StorageOutagePolicy.retryQueue("periodic-save"));
        assertTrue(StorageOutagePolicy.retryQueue("empty-island-save"));
        assertFalse(StorageOutagePolicy.retryQueue("snapshot-restore"));
        assertFalse(StorageOutagePolicy.retryQueue(null));
    }

    @Test
    void namesMetricsNeededToOperateTheOutageQueue() {
        assertEquals(
            "storageSaveRetryQueueTotal,periodicSaveRetryQueue,emptySaveRetryQueue,storageOperationFailuresTotal",
            StorageOutagePolicy.OBSERVABILITY_KEYS
        );
    }
}
