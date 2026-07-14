package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingJobCompletionStoreTest {
    @TempDir
    Path root;

    @Test
    void pendingPayloadSurvivesRestartAndRemoval() throws Exception {
        Path journal = root.resolve("pending.bin");
        UUID jobId = UUID.randomUUID();
        PendingJobCompletionStore first = new PendingJobCompletionStore(journal);
        first.put(jobId, Map.of("snapshotNo", "42", "checksum", "abc"));

        PendingJobCompletionStore restarted = new PendingJobCompletionStore(journal);

        assertEquals(Map.of("snapshotNo", "42", "checksum", "abc"), restarted.find(jobId).orElseThrow());
        restarted.remove(jobId);
        assertTrue(new PendingJobCompletionStore(journal).find(jobId).isEmpty());
    }

    @Test
    void laterLocalSuccessAtomicallyReplacesTheSameJobPayload() throws Exception {
        Path journal = root.resolve("replace.bin");
        UUID jobId = UUID.randomUUID();
        PendingJobCompletionStore store = new PendingJobCompletionStore(journal);

        store.put(jobId, Map.of("snapshotNo", "1"));
        store.put(jobId, Map.of("snapshotNo", "2", "reason", "BEFORE_DELETE"));

        assertEquals(Map.of("snapshotNo", "2", "reason", "BEFORE_DELETE"), new PendingJobCompletionStore(journal).find(jobId).orElseThrow());
        assertEquals(1, store.size());
    }

    @Test
    void truncatedJournalFailsClosedInsteadOfDiscardingLocalSuccess() throws Exception {
        Path journal = root.resolve("corrupt.bin");
        Files.write(journal, new byte[] {0x43, 0x49, 0x4a});

        assertThrows(IOException.class, () -> new PendingJobCompletionStore(journal));
    }
}
