package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingSnapshotRecordsTest {
    @Test
    void retainsFailedRecordsAndPreventsConcurrentDuplicateClaims() {
        PendingSnapshotRecords records = new PendingSnapshotRecords();
        PendingSnapshotRecords.PendingSnapshotRecord record = record(UUID.randomUUID(), 10L);
        records.enqueue(record);

        assertEquals(1, records.claimAll().size());
        assertTrue(records.claimAll().isEmpty());

        records.failed(record);
        assertEquals(1, records.size());
        assertEquals(record, records.claimAll().getFirst());

        records.completed(record);
        assertEquals(0, records.size());
        assertTrue(records.claimAll().isEmpty());
    }

    @Test
    void keepsOriginalSnapshotUntilItIsRecorded() {
        UUID islandId = UUID.randomUUID();
        PendingSnapshotRecords records = new PendingSnapshotRecords();
        PendingSnapshotRecords.PendingSnapshotRecord first = record(islandId, 10L);
        records.enqueue(first);
        records.enqueue(record(islandId, 11L));

        assertEquals(first, records.claim(islandId).getFirst());
    }

    private PendingSnapshotRecords.PendingSnapshotRecord record(UUID islandId, long snapshotNo) {
        return new PendingSnapshotRecords.PendingSnapshotRecord(
            islandId, snapshotNo, "snapshot", "PERIODIC", "checksum", 10L, "node-a", 99L
        );
    }
}
