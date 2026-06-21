package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreAdminNodeCommandClient implements AdminNodeCommandClient {
    private final CoreApiClient delegate;

    public CoreAdminNodeCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminNodeActionView> drainNode(String nodeId) {
        return delegate.drainNode(requireNode(nodeId)).thenApply(CoreAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> undrainNode(String nodeId) {
        return delegate.undrainNode(requireNode(nodeId)).thenApply(CoreAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> sweepNode(String nodeId) {
        return delegate.sweepNode(requireNode(nodeId)).thenApply(CoreAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> kickAllNode(String nodeId, String reason) {
        return delegate.kickAllNode(requireNode(nodeId), normalizeReason(reason)).thenApply(CoreAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> shutdownNodeSafely(String nodeId, String reason) {
        return delegate.shutdownNodeSafely(requireNode(nodeId), normalizeReason(reason)).thenApply(CoreAdminNodeCommandClient::actionResult);
    }

    private static AdminNodeActionView actionResult(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        return new AdminNodeActionView(
            accepted,
            SimpleJson.text(root.get("code")),
            SimpleJson.text(root.get("nodeId")),
            SimpleJson.text(root.get("operation")),
            strings(root.get("nodes")),
            (int) SimpleJson.number(root.get("recoveryRequired"))
        );
    }

    private static List<String> strings(Object value) {
        return SimpleJson.list(value).stream()
            .map(SimpleJson::text)
            .filter(text -> !text.isBlank())
            .toList();
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
}
