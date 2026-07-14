package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperIslandJobWorkerPolicyTest {
    @Test
    void completionReportFailureDoesNotFailDurablySavedJob() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/job/PaperIslandJobWorker.java"));
        String reporter = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/job/PaperJobCompletionReporter.java"));

        assertTrue(source.contains("reportComplete(job, completePayload"), "durable completion reports must go through retry-aware reporting");
        assertTrue(source.contains("PaperJobCompletionReporter"), "completion reporting must be isolated in a tested component");
        assertTrue(reporter.contains("leaving claimed job for retry"), "completion reporting failure must leave Core claim recovery as the retry path");
        assertTrue(source.contains("catch (PaperJobCompletionReporter.CompletionReportFailedException ignored)"), "worker must not convert completion-report failures into jobSource.fail");
        assertTrue(source.contains("pendingCompletions.put(job, payload)"), "local success and its claim lease must be journaled before reporting to Core");
        assertTrue(source.contains("replayPendingCompletion(job)"), "retried jobs must replay completion without repeating world mutation");
        assertTrue(source.contains("replayPendingCompletions();"), "saved claim leases must be replayed before asking Core for more work");
        assertTrue(source.indexOf("replayPendingCompletions();") < source.indexOf("jobSource.claim("), "orphaned completion receipts must be reconciled before new mutations are claimed");
        int replay = source.indexOf("if (replayPendingCompletion(job))");
        int saveBranch = source.indexOf("if (job.type() == IslandJobType.SAVE_ISLAND");
        assertTrue(replay >= 0 && replay < saveBranch, "completion replay must short-circuit before any world mutation branch");
        int journal = source.indexOf("pendingCompletions.put(job, payload)");
        int report = source.indexOf("completionReporter.report(job, payload)", journal);
        assertTrue(journal >= 0 && journal < report, "the local-success journal must be durable before Core is contacted");
    }
}
