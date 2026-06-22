package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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

    static AdminNodeActionView actionResult(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new AdminNodeActionView(
            CoreJson.accepted(root),
            CoreJson.text(root, "code"),
            CoreJson.text(root, "nodeId"),
            CoreJson.text(root, "operation"),
            CoreJson.strings(root, "nodes"),
            (int) CoreJson.number(root, "recoveryRequired")
        );
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
