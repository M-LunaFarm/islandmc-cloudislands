package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreAdminNodeQueryClient implements AdminNodeQueryClient {
    private final CoreApiClient delegate;

    public CoreAdminNodeQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminNodeSummaryView> listNodesSummary() {
        return delegate.listNodes().thenApply(CoreAdminNodeQueryClient::summary);
    }

    @Override
    public CompletableFuture<CoreGuiViews.NodeSummaryView> nodeInfo(String nodeId) {
        String normalizedNodeId = requireNode(nodeId);
        return delegate.nodeInfo(normalizedNodeId).thenApply(body -> CoreGuiViews.nodeSummary(normalizedNodeId, body));
    }

    @Override
    public CompletableFuture<AdminNodeSummaryView> nodeIslandsSummary(String nodeId, int limit) {
        return delegate.nodeIslands(requireNode(nodeId), Math.max(1, Math.min(limit, 100)))
            .thenApply(CoreAdminNodeQueryClient::summary);
    }

    private static AdminNodeSummaryView summary(String body) {
        Object parsed = SimpleJson.parse(body);
        Map<?, ?> root = SimpleJson.object(parsed);
        if (!root.isEmpty()) {
            String code = text(root, "code");
            if (!code.isBlank()) {
                return new AdminNodeSummaryView("code=" + code);
            }
            String nodeId = text(root, "nodeId");
            if (!nodeId.isBlank()) {
                long count = SimpleJson.number(root.get("count"));
                return count > 0L
                    ? new AdminNodeSummaryView("node=" + compactId(nodeId) + " count=" + count)
                    : new AdminNodeSummaryView("node=" + compactId(nodeId));
            }
            List<?> nodes = SimpleJson.list(root.get("nodes"));
            if (!nodes.isEmpty()) {
                long nodeCount = number(root, "nodeCount");
                return new AdminNodeSummaryView(
                    "nodes=" + nodes.size(),
                    nodeCount > 0L ? nodeCount : nodes.size(),
                    number(root, "routeCandidateCount"),
                    number(root, "staleNodeCount"),
                    number(root, "heartbeatTimeoutSeconds")
                );
            }
        }
        List<?> values = SimpleJson.list(parsed);
        if (!values.isEmpty()) {
            return new AdminNodeSummaryView("nodes=" + values.size(), values.size(), 0L, 0L, 0L);
        }
        if (body == null || body.isBlank()) {
            return new AdminNodeSummaryView("");
        }
        return new AdminNodeSummaryView(clip(body, 180));
    }

    private static String requireNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return nodeId.trim();
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
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
}
