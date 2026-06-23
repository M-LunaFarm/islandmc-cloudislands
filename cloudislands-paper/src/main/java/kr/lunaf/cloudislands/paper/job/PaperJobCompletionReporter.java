package kr.lunaf.cloudislands.paper.job;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.protocol.job.IslandJob;

final class PaperJobCompletionReporter {
    private final String nodeId;
    private final CompletionSink sink;
    private final Consumer<String> warnings;

    PaperJobCompletionReporter(String nodeId, CompletionSink sink, Consumer<String> warnings) {
        this.nodeId = nodeId == null ? "" : nodeId;
        this.sink = sink;
        this.warnings = warnings == null ? ignored -> { } : warnings;
    }

    void report(UUID jobId, Map<String, String> payload) {
        report(new IslandJob(jobId, null, null, "", 0, Map.of(), java.time.Instant.EPOCH), payload);
    }

    void report(IslandJob job, Map<String, String> payload) {
        try {
            sink.complete(nodeId, job, payload);
        } catch (RuntimeException exception) {
            warnings.accept("CloudIslands job completion report failed; leaving claimed job for retry: "
                + job.jobId() + " " + exception.getMessage());
            throw new CompletionReportFailedException(exception);
        }
    }

    @FunctionalInterface
    interface CompletionSink {
        void complete(String nodeId, IslandJob job, Map<String, String> payload);
    }

    static final class CompletionReportFailedException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        CompletionReportFailedException(Throwable cause) {
            super(cause);
        }
    }
}
