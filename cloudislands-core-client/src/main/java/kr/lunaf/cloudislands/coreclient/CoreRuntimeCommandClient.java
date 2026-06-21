package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class CoreRuntimeCommandClient implements RuntimeCommandClient {
    private final CoreApiClient delegate;

    public CoreRuntimeCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<RuntimeActionView> publishHeartbeat(NodeHeartbeatRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request is required");
        }
        return delegate.publishHeartbeatResult(request).thenApply(body -> action(body, "HEARTBEAT_ACCEPTED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta) {
        requireId(islandId, "islandId");
        String safeMaterialKey = materialKey == null ? "" : materialKey.trim();
        if (safeMaterialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
        return delegate.recordBlockDeltaResult(islandId, safeMaterialKey, delta).thenApply(body -> action(body, "BLOCK_DELTA_RECORDED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
        return delegate.completeJobResult(requireNode(nodeId), requireId(jobId, "jobId"), payload == null ? Map.of() : payload)
            .thenApply(body -> action(body, "JOB_COMPLETED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage) {
        return delegate.failJobResult(requireNode(nodeId), requireId(jobId, "jobId"), errorMessage == null ? "" : errorMessage)
            .thenApply(body -> action(body, "JOB_FAILED"));
    }

    private static RuntimeActionView action(String body, String fallbackCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body == null || body.isBlank() ? "{}" : body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("ok"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? fallbackCode : "FAILED";
        }
        return new RuntimeActionView(accepted, code);
    }

    private static UUID requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return id;
    }

    private static String requireNode(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return nodeId.trim();
    }
}
