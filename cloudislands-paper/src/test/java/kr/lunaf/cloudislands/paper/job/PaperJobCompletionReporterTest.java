package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaperJobCompletionReporterTest {
    @Test
    void completionFailureIsTypedAndLeavesRetryDecisionToCoreClaimRecovery() {
        UUID jobId = UUID.randomUUID();
        List<String> warnings = new ArrayList<>();
        PaperJobCompletionReporter reporter = new PaperJobCompletionReporter(
            "island-node-1",
            (_nodeId, _jobId, _payload) -> {
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
            (nodeId, completedJobId, payload) -> calls.add(nodeId + ":" + completedJobId + ":" + payload.get("snapshotNo")),
            ignored -> { }
        );

        reporter.report(jobId, Map.of("snapshotNo", "18"));

        assertEquals(List.of("island-node-1:" + jobId + ":18"), calls);
    }
}
