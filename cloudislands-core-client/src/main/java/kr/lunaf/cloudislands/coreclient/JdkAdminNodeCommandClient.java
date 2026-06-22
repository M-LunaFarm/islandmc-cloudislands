package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class JdkAdminNodeCommandClient implements AdminNodeCommandClient {
    private final JdkCoreApiClient core;

    JdkAdminNodeCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminNodeActionView> drainNode(String nodeId) {
        return core.postWithResultBody("/v1/admin/nodes/drain", JdkCoreApiClient.jsonObject("nodeId", requireNode(nodeId))).thenApply(JdkAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> undrainNode(String nodeId) {
        return core.postWithResultBody("/v1/admin/nodes/undrain", JdkCoreApiClient.jsonObject("nodeId", requireNode(nodeId))).thenApply(JdkAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> sweepNode(String nodeId) {
        return core.postWithResultBody("/v1/admin/nodes/sweep", JdkCoreApiClient.jsonObject("nodeId", requireNode(nodeId))).thenApply(JdkAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> kickAllNode(String nodeId, String reason) {
        return core.postWithResultBody("/v1/admin/nodes/kickall", JdkCoreApiClient.jsonObject("nodeId", requireNode(nodeId), "reason", normalizeReason(reason))).thenApply(JdkAdminNodeCommandClient::actionResult);
    }

    @Override
    public CompletableFuture<AdminNodeActionView> shutdownNodeSafely(String nodeId, String reason) {
        return core.postWithResultBody("/v1/admin/nodes/shutdown-safe", JdkCoreApiClient.jsonObject("nodeId", requireNode(nodeId), "reason", normalizeReason(reason))).thenApply(JdkAdminNodeCommandClient::actionResult);
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
