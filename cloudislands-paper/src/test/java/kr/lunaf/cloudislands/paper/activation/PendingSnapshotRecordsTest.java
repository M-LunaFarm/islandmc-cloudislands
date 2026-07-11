package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingSnapshotRecordsTest {
    @TempDir
    Path temporaryDirectory;

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
    void recordsMultipleSnapshotsForOneIslandInOrder() {
        UUID islandId = UUID.randomUUID();
        PendingSnapshotRecords records = new PendingSnapshotRecords();
        PendingSnapshotRecords.PendingSnapshotRecord first = record(islandId, 10L);
        PendingSnapshotRecords.PendingSnapshotRecord second = record(islandId, 11L);
        records.enqueue(first);
        records.enqueue(second);

        assertEquals(first, records.claim(islandId).getFirst());
        assertTrue(records.claim(islandId).isEmpty());
        assertTrue(records.completed(first));
        assertEquals(second, records.claim(islandId).getFirst());
    }

    @Test
    void restoresPendingRecordsAfterPaperRestart() {
        Path journal = temporaryDirectory.resolve("pending-periodic-snapshots.tsv");
        PendingSnapshotRecords.PendingSnapshotRecord expected = new PendingSnapshotRecords.PendingSnapshotRecord(
            UUID.randomUUID(), 42L, "islands/경로 with spaces/bundle.tar.zst", "PERIODIC", "abc+/=", 2048L, "node-a", 123L
        );

        PendingSnapshotRecords beforeRestart = new PendingSnapshotRecords(journal);
        assertTrue(beforeRestart.enqueue(expected));
        assertTrue(Files.isRegularFile(journal));

        PendingSnapshotRecords afterRestart = new PendingSnapshotRecords(journal);
        assertEquals(1, afterRestart.size());
        assertEquals(expected, afterRestart.claimAll().getFirst());
        assertTrue(afterRestart.completed(expected));

        PendingSnapshotRecords afterCompletionRestart = new PendingSnapshotRecords(journal);
        assertEquals(0, afterCompletionRestart.size());
    }

    @Test
    void restoresMultipleOrderedRecordsForOneIslandAfterRestart() {
        Path journal = temporaryDirectory.resolve("pending-periodic-snapshots.tsv");
        UUID islandId = UUID.randomUUID();
        PendingSnapshotRecords.PendingSnapshotRecord first = record(islandId, 7L);
        PendingSnapshotRecords.PendingSnapshotRecord second = record(islandId, 8L);
        PendingSnapshotRecords writer = new PendingSnapshotRecords(journal);
        assertTrue(writer.enqueue(first));
        assertTrue(writer.enqueue(second));

        PendingSnapshotRecords restored = new PendingSnapshotRecords(journal);
        assertEquals(2, restored.size());
        assertEquals(first, restored.claim(islandId).getFirst());
        assertTrue(restored.completed(first));
        assertEquals(second, restored.claim(islandId).getFirst());
    }

    @Test
    void isolatesInvalidJournalRecordsWithoutDroppingValidRecords() throws Exception {
        Path journal = temporaryDirectory.resolve("pending-periodic-snapshots.tsv");
        PendingSnapshotRecords.PendingSnapshotRecord expected = record(UUID.randomUUID(), 7L);
        PendingSnapshotRecords writer = new PendingSnapshotRecords(journal);
        assertTrue(writer.enqueue(expected));
        Files.writeString(journal, Files.readString(journal) + "invalid-record\n");

        PendingSnapshotRecords restored = new PendingSnapshotRecords(journal);
        assertEquals(1, restored.size());
        assertEquals(1, restored.discardedJournalRecords());
        assertFalse(restored.claimAll().isEmpty());
    }

    private PendingSnapshotRecords.PendingSnapshotRecord record(UUID islandId, long snapshotNo) {
        return new PendingSnapshotRecords.PendingSnapshotRecord(
            islandId, snapshotNo, "snapshot", "PERIODIC", "checksum", 10L, "node-a", 99L
        );
    }
}
