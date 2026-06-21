package kr.lunaf.cloudislands.paper.application;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandAdminNodeUseCase {
    private final CoreApiClient coreApiClient;

    public IslandAdminNodeUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> listNodes() {
        return coreApiClient.listNodes();
    }

    public CompletableFuture<String> nodeInfo(String nodeId) {
        return coreApiClient.nodeInfo(requireNode(nodeId));
    }

    public CompletableFuture<String> nodeIslands(String nodeId, int limit) {
        return coreApiClient.nodeIslands(requireNode(nodeId), Math.max(1, Math.min(limit, 100)));
    }

    public CompletableFuture<String> drain(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.drain", () -> coreApiClient.drainNode(normalizedNodeId));
    }

    public CompletableFuture<String> undrain(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.undrain", () -> coreApiClient.undrainNode(normalizedNodeId));
    }

    public CompletableFuture<String> sweep(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.sweep", () -> coreApiClient.sweepNode(normalizedNodeId));
    }

    public CompletableFuture<String> kickAll(String nodeId, String reason, IdempotentMutationRunner runner) {
        requireIdempotentMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutateIdempotent("admin.node.kickall", () -> coreApiClient.kickAllNode(normalizedNodeId, normalizeReason(reason)));
    }

    public CompletableFuture<String> shutdownSafely(String nodeId, String reason, IdempotentMutationRunner runner) {
        requireIdempotentMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutateIdempotent("admin.node.shutdown-safe", () -> coreApiClient.shutdownNodeSafely(normalizedNodeId, normalizeReason(reason)));
    }

    private static String requireNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return nodeId.trim();
    }

    private static String normalizeReason(String reason) {
        return reason == null ? "" : reason;
    }

    private static void requireMutationRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static void requireIdempotentMutationRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }
}
