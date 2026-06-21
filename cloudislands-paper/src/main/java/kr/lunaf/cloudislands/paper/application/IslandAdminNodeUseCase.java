package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.AdminNodeQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreAdminNodeQueryClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.NodeSummaryView;

public final class IslandAdminNodeUseCase {
    private final CoreApiClient coreApiClient;
    private final AdminNodeQueryClient adminNodeQueries;

    public IslandAdminNodeUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.adminNodeQueries = new CoreAdminNodeQueryClient(coreApiClient);
    }

    IslandAdminNodeUseCase(CoreApiClient coreApiClient, AdminNodeQueryClient adminNodeQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (adminNodeQueries == null) {
            throw new IllegalArgumentException("adminNodeQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.adminNodeQueries = adminNodeQueries;
    }

    public CompletableFuture<AdminNodeSummary> listNodesSummary() {
        return adminNodeQueries.listNodesSummary().thenApply(summary -> new AdminNodeSummary(summary.text()));
    }

    public CompletableFuture<NodeSummaryView> nodeInfoView(String nodeId) {
        String normalizedNodeId = requireNode(nodeId);
        return adminNodeQueries.nodeInfo(normalizedNodeId).thenApply(IslandAdminNodeUseCase::nodeSummaryView);
    }

    public CompletableFuture<AdminNodeSummary> nodeIslandsSummary(String nodeId, int limit) {
        return adminNodeQueries.nodeIslandsSummary(nodeId, limit).thenApply(summary -> new AdminNodeSummary(summary.text()));
    }

    private CompletableFuture<String> drainBody(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.drain", () -> coreApiClient.drainNode(normalizedNodeId));
    }

    public CompletableFuture<AdminNodeActionResult> drainAction(String nodeId, MutationRunner runner) {
        return drainBody(nodeId, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    private CompletableFuture<String> undrainBody(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.undrain", () -> coreApiClient.undrainNode(normalizedNodeId));
    }

    public CompletableFuture<AdminNodeActionResult> undrainAction(String nodeId, MutationRunner runner) {
        return undrainBody(nodeId, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    private CompletableFuture<String> sweepBody(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.sweep", () -> coreApiClient.sweepNode(normalizedNodeId));
    }

    public CompletableFuture<AdminNodeActionResult> sweepAction(String nodeId, MutationRunner runner) {
        return sweepBody(nodeId, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    private CompletableFuture<String> kickAllBody(String nodeId, String reason, IdempotentMutationRunner runner) {
        requireIdempotentMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutateIdempotent("admin.node.kickall", () -> coreApiClient.kickAllNode(normalizedNodeId, normalizeReason(reason)));
    }

    public CompletableFuture<AdminNodeActionResult> kickAllAction(String nodeId, String reason, IdempotentMutationRunner runner) {
        return kickAllBody(nodeId, reason, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    private CompletableFuture<String> shutdownSafelyBody(String nodeId, String reason, IdempotentMutationRunner runner) {
        requireIdempotentMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutateIdempotent("admin.node.shutdown-safe", () -> coreApiClient.shutdownNodeSafely(normalizedNodeId, normalizeReason(reason)));
    }

    public CompletableFuture<AdminNodeActionResult> shutdownSafelyAction(String nodeId, String reason, IdempotentMutationRunner runner) {
        return shutdownSafelyBody(nodeId, reason, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    private static NodeSummaryView nodeSummaryView(CoreGuiViews.NodeSummaryView view) {
        return new NodeSummaryView(
            view.nodeId(),
            view.state(),
            view.pool(),
            view.players(),
            view.softPlayerCap(),
            view.hardPlayerCap(),
            view.activeIslands(),
            view.maxActiveIslands(),
            view.activationQueue(),
            view.maxActivationQueue(),
            view.mspt()
        );
    }

    private static AdminNodeActionResult actionResult(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        String nodeId = text(root, "nodeId");
        String operation = text(root, "operation");
        return new AdminNodeActionResult(accepted, code, nodeId, operation);
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

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record AdminNodeSummary(String text) {
        public AdminNodeSummary {
            text = text == null ? "" : text;
        }
    }

    public record AdminNodeActionResult(boolean accepted, String code, String nodeId, String operation) {
        public AdminNodeActionResult {
            code = code == null ? "" : code;
            nodeId = nodeId == null ? "" : nodeId;
            operation = operation == null ? "" : operation;
        }
    }
}
