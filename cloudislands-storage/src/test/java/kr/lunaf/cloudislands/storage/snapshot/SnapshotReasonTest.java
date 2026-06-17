package kr.lunaf.cloudislands.storage.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SnapshotReasonTest {
    @Test
    void listsEveryRequiredSnapshotTriggerFromTheBackupPolicy() {
        assertEquals(
            Set.of(
                SnapshotReason.CREATED,
                SnapshotReason.DEACTIVATION,
                SnapshotReason.PERIODIC,
                SnapshotReason.BEFORE_DELETE,
                SnapshotReason.BEFORE_RESET,
                SnapshotReason.BEFORE_MIGRATION,
                SnapshotReason.MANUAL
            ),
            SnapshotReason.requiredSnapshotTriggers()
        );
    }

    @Test
    void separatesRollbackPreRestoreSnapshotFromNormalRequiredTriggers() {
        assertEquals(SnapshotReason.BEFORE_RESTORE, SnapshotReason.preRestoreSnapshotReason());
        assertFalse(SnapshotReason.BEFORE_RESTORE.requiredTrigger());
        assertTrue(SnapshotReason.BEFORE_RESET.requiredTrigger());
    }

    @Test
    void marksOnlyAdminManualSnapshotsForManualRetentionBucket() {
        assertTrue(SnapshotReason.MANUAL.manualRetentionBucket());
        assertFalse(SnapshotReason.PERIODIC.manualRetentionBucket());
        assertFalse(SnapshotReason.BEFORE_DELETE.manualRetentionBucket());
    }
}
