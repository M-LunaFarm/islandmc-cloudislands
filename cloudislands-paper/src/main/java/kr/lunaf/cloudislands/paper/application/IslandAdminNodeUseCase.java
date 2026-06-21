package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.NodeSummaryView;

public final class IslandAdminNodeUseCase {
    private final CoreApiClient coreApiClient;

    public IslandAdminNodeUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    private CompletableFuture<String> listNodeBodies() {
        return coreApiClient.listNodes();
    }

    public CompletableFuture<AdminNodeSummary> listNodesSummary() {
        return listNodeBodies().thenApply(IslandAdminNodeUseCase::summary);
    }

    private CompletableFuture<String> nodeInfoBody(String nodeId) {
        String normalizedNodeId = requireNode(nodeId);
        return coreApiClient.nodeInfo(normalizedNodeId);
    }

    public CompletableFuture<NodeSummaryView> nodeInfoView(String nodeId) {
        String normalizedNodeId = requireNode(nodeId);
        return nodeInfoBody(normalizedNodeId).thenApply(body -> PaperGuiViews.nodeSummary(normalizedNodeId, body));
    }

    private CompletableFuture<String> nodeIslandBodies(String nodeId, int limit) {
        return coreApiClient.nodeIslands(requireNode(nodeId), Math.max(1, Math.min(limit, 100)));
    }

    public CompletableFuture<AdminNodeSummary> nodeIslandsSummary(String nodeId, int limit) {
        return nodeIslandBodies(nodeId, limit).thenApply(IslandAdminNodeUseCase::summary);
    }

    public CompletableFuture<String> drain(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.drain", () -> coreApiClient.drainNode(normalizedNodeId));
    }

    public CompletableFuture<AdminNodeActionResult> drainAction(String nodeId, MutationRunner runner) {
        return drain(nodeId, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    public CompletableFuture<String> undrain(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.undrain", () -> coreApiClient.undrainNode(normalizedNodeId));
    }

    public CompletableFuture<AdminNodeActionResult> undrainAction(String nodeId, MutationRunner runner) {
        return undrain(nodeId, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    public CompletableFuture<String> sweep(String nodeId, MutationRunner runner) {
        requireMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutate("admin.node.sweep", () -> coreApiClient.sweepNode(normalizedNodeId));
    }

    public CompletableFuture<AdminNodeActionResult> sweepAction(String nodeId, MutationRunner runner) {
        return sweep(nodeId, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    public CompletableFuture<String> kickAll(String nodeId, String reason, IdempotentMutationRunner runner) {
        requireIdempotentMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutateIdempotent("admin.node.kickall", () -> coreApiClient.kickAllNode(normalizedNodeId, normalizeReason(reason)));
    }

    public CompletableFuture<AdminNodeActionResult> kickAllAction(String nodeId, String reason, IdempotentMutationRunner runner) {
        return kickAll(nodeId, reason, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    public CompletableFuture<String> shutdownSafely(String nodeId, String reason, IdempotentMutationRunner runner) {
        requireIdempotentMutationRunner(runner);
        String normalizedNodeId = requireNode(nodeId);
        return runner.mutateIdempotent("admin.node.shutdown-safe", () -> coreApiClient.shutdownNodeSafely(normalizedNodeId, normalizeReason(reason)));
    }

    public CompletableFuture<AdminNodeActionResult> shutdownSafelyAction(String nodeId, String reason, IdempotentMutationRunner runner) {
        return shutdownSafely(nodeId, reason, runner).thenApply(IslandAdminNodeUseCase::actionResult);
    }

    private static AdminNodeSummary summary(String body) {
        Object parsed = SimpleJson.parse(body);
        Map<?, ?> root = SimpleJson.object(parsed);
        if (!root.isEmpty()) {
            String code = text(root, "code");
            if (!code.isBlank()) {
                return new AdminNodeSummary("code=" + code);
            }
            String nodeId = text(root, "nodeId");
            if (!nodeId.isBlank()) {
                long count = SimpleJson.number(root.get("count"));
                return count > 0L
                    ? new AdminNodeSummary("node=" + compactId(nodeId) + " count=" + count)
                    : new AdminNodeSummary("node=" + compactId(nodeId));
            }
            List<?> nodes = SimpleJson.list(root.get("nodes"));
            if (!nodes.isEmpty()) {
                return new AdminNodeSummary("nodes=" + nodes.size());
            }
        }
        List<?> values = SimpleJson.list(parsed);
        if (!values.isEmpty()) {
            return new AdminNodeSummary("nodes=" + values.size());
        }
        if (body == null || body.isBlank()) {
            return new AdminNodeSummary("");
        }
        return new AdminNodeSummary(clip(body, 180));
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

    private static String compactId(String value) {
        if (value == null || value.length() != 36 || !value.contains("-")) {
            return value == null ? "" : value;
        }
        return new StringBuilder(8).append(value, 0, 8).toString();
    }

    private static String clip(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return new StringBuilder(maxLength + 3).append(value, 0, maxLength).append("...").toString();
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
