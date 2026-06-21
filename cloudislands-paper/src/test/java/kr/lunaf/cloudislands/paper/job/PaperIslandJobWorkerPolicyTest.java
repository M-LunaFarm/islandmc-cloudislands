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
    }
}
