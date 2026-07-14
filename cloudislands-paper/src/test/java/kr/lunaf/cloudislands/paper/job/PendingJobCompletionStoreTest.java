package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
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

    @Test
    void claimedLeaseSurvivesRestartForProactiveReplay() throws Exception {
        Path journal = root.resolve("claimed.bin");
        UUID jobId = UUID.randomUUID();
        JobClaimLease lease = new JobClaimLease(jobId, "stream-1", "paper-1", "token-1", 7L, Instant.parse("2026-07-15T00:00:00Z"), 2);
        IslandJob job = new IslandJob(jobId, IslandJobType.DELETE_ISLAND, UUID.randomUUID(), "paper-1", 0, Map.of(), Instant.EPOCH, lease);
        PendingJobCompletionStore store = new PendingJobCompletionStore(journal);

        store.put(job, Map.of("snapshotNo", "42"));
        PendingJobCompletionStore.PendingCompletion replay = new PendingJobCompletionStore(journal).replayable().getFirst();

        assertEquals(jobId, replay.jobId());
        assertEquals(lease, replay.claimLease());
        assertEquals(Map.of("snapshotNo", "42"), replay.payload());
    }

    @Test
    void versionOneJournalRemainsReadableDuringRollingUpgrade() throws Exception {
        Path journal = root.resolve("version-one.bin");
        UUID jobId = UUID.randomUUID();
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(journal))) {
            output.writeInt(0x43494A43);
            output.writeInt(1);
            output.writeInt(1);
            output.writeUTF(jobId.toString());
            output.writeInt(1);
            output.writeUTF("snapshotNo");
            output.writeUTF("42");
        }

        PendingJobCompletionStore upgraded = new PendingJobCompletionStore(journal);

        assertEquals(Map.of("snapshotNo", "42"), upgraded.find(jobId).orElseThrow());
        assertTrue(upgraded.replayable().isEmpty());
    }

    @Test
    void writeFailureRetainsTheCompletionInMemoryForTheRunningNode() throws Exception {
        Path blockedParent = root.resolve("blocked-parent");
        PendingJobCompletionStore store = new PendingJobCompletionStore(blockedParent.resolve("pending.bin"));
        Files.writeString(blockedParent, "not-a-directory");
        UUID jobId = UUID.randomUUID();

        assertThrows(IOException.class, () -> store.put(jobId, Map.of("snapshotNo", "42")));
        assertEquals(Map.of("snapshotNo", "42"), store.find(jobId).orElseThrow());
    }
}
