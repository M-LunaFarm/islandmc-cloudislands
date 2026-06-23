package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;

class PaperJobCompletionReporterTest {
    @Test
    void completionFailureIsTypedAndLeavesRetryDecisionToCoreClaimRecovery() {
        UUID jobId = UUID.randomUUID();
        List<String> warnings = new ArrayList<>();
        PaperJobCompletionReporter reporter = new PaperJobCompletionReporter(
            "island-node-1",
            (_nodeId, _job, _payload) -> {
                throw new IllegalStateException("core unavailable");
            },
            warnings::add
        );

        PaperJobCompletionReporter.CompletionReportFailedException exception = assertThrows(
            PaperJobCompletionReporter.CompletionReportFailedException.class,
            () -> reporter.report(jobId, Map.of("snapshotNo", "17"))
        );

        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertEquals(1, warnings.size());
        assertTrue(warnings.getFirst().contains("leaving claimed job for retry"));
        assertTrue(warnings.getFirst().contains(jobId.toString()));
    }

    @Test
    void successfulCompletionPassesNodeJobAndPayloadToSink() {
        UUID jobId = UUID.randomUUID();
        List<String> calls = new ArrayList<>();
        PaperJobCompletionReporter reporter = new PaperJobCompletionReporter(
            "island-node-1",
            (nodeId, completedJob, payload) -> calls.add(nodeId + ":" + completedJob.jobId() + ":" + payload.get("snapshotNo")),
            ignored -> { }
        );

        reporter.report(jobId, Map.of("snapshotNo", "18"));

        assertEquals(List.of("island-node-1:" + jobId + ":18"), calls);
    }

    @Test
    void successfulCompletionPassesClaimedJobToSink() {
        UUID jobId = UUID.randomUUID();
        IslandJob job = new IslandJob(jobId, IslandJobType.SAVE_ISLAND, UUID.randomUUID(), "island-node-1", 0, Map.of(), java.time.Instant.EPOCH);
        List<IslandJob> completed = new ArrayList<>();
        PaperJobCompletionReporter reporter = new PaperJobCompletionReporter(
            "island-node-1",
            (_nodeId, completedJob, _payload) -> completed.add(completedJob),
            ignored -> { }
        );

        reporter.report(job, Map.of("snapshotNo", "19"));

        assertEquals(List.of(job), completed);
    }
}
