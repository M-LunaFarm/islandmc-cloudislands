package kr.lunaf.cloudislands.paper.job;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.RuntimeActionView;
import kr.lunaf.cloudislands.coreclient.RuntimeCommandClient;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

class CoreBackedIslandJobSourceTest {
    @Test
    void completionWaitsForTheAsyncCoreResult() {
        TestRuntimeCommands commands = new TestRuntimeCommands();
        CoreBackedIslandJobSource source = source(commands);
        UUID jobId = UUID.randomUUID();
        IslandJob job = new IslandJob(jobId, IslandJobType.DELETE_ISLAND, UUID.randomUUID(), "paper-1", 0, Map.of(), Instant.EPOCH);

        commands.next.set(CompletableFuture.failedFuture(new IllegalStateException("core unavailable")));
        assertThrows(CompletionException.class, () -> source.complete("paper-1", job, Map.of("snapshotNo", "4")));

        commands.next.set(CompletableFuture.completedFuture(new RuntimeActionView(true, "OK")));
        assertDoesNotThrow(() -> source.complete("paper-1", job, Map.of("snapshotNo", "4")));
    }

    @Test
    void failureReportAlsoWaitsForTheAsyncCoreResult() {
        TestRuntimeCommands commands = new TestRuntimeCommands();
        CoreBackedIslandJobSource source = source(commands);

        commands.next.set(CompletableFuture.failedFuture(new IllegalStateException("core unavailable")));
        assertThrows(CompletionException.class, () -> source.fail("paper-1", UUID.randomUUID(), "FAILED"));
    }

    private CoreBackedIslandJobSource source(RuntimeCommandClient commands) {
        return new CoreBackedIslandJobSource(new CoreApiClient() {
            @Override
            public RuntimeCommandClient runtimeCommands() {
                return commands;
            }
        });
    }

    private static final class TestRuntimeCommands implements RuntimeCommandClient {
        private final AtomicReference<CompletableFuture<RuntimeActionView>> next = new AtomicReference<>();

        @Override
        public CompletableFuture<RuntimeActionView> publishHeartbeat(NodeHeartbeatRequest request) {
            return next.get();
        }

        @Override
        public CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta) {
            return next.get();
        }

        @Override
        public CompletableFuture<RuntimeActionView> replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
            return next.get();
        }

        @Override
        public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
            return next.get();
        }

        @Override
        public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, JobClaimLease claimLease, Map<String, String> payload) {
            return next.get();
        }

        @Override
        public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage) {
            return next.get();
        }

        @Override
        public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, JobClaimLease claimLease, String errorMessage) {
            return next.get();
        }
    }
}
