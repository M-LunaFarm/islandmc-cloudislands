package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperIslandJobWorkerPolicyTest {
    @Test
    void completionReportFailureDoesNotFailDurablySavedJob() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/job/PaperIslandJobWorker.java"));

        assertTrue(source.contains("reportComplete(job, completePayload"), "durable completion reports must go through retry-aware reporting");
        assertTrue(source.contains("CompletionReportFailedException"), "completion reporting failure must be separated from job execution failure");
        assertTrue(source.contains("leaving claimed job for retry"), "completion reporting failure must leave Core claim recovery as the retry path");
        assertTrue(source.contains("catch (CompletionReportFailedException ignored)"), "worker must not convert completion-report failures into jobSource.fail");
    }
}
