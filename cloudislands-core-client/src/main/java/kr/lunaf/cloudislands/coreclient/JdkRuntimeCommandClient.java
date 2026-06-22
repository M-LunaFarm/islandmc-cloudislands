package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class JdkRuntimeCommandClient implements RuntimeCommandClient {
    private final JdkCoreApiClient core;

    public JdkRuntimeCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<RuntimeActionView> publishHeartbeat(NodeHeartbeatRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("request is required"));
        }
        return core.postWithResultBody("/v1/nodes/heartbeat", CoreJsonPayload.object(
                "protocolVersion", request.protocolVersion(),
                "nodeId", request.nodeId(),
                "pool", request.pool(),
                "velocityServerName", request.velocityServerName(),
                "nodeVersion", request.nodeVersion(),
                "state", request.state().name(),
                "players", request.players(),
                "softPlayerCap", request.softPlayerCap(),
                "hardPlayerCap", request.hardPlayerCap(),
                "reservedSlots", request.reservedSlots(),
                "activeIslands", request.activeIslands(),
                "maxActiveIslands", request.maxActiveIslands(),
                "mspt", request.mspt(),
                "activationQueue", request.activationQueue(),
                "maxActivationQueue", request.maxActivationQueue(),
                "chunkLoadPressure", request.chunkLoadPressure(),
                "heapUsedMb", request.heapUsedMb(),
                "heapMaxMb", request.heapMaxMb(),
                "recentFailurePenalty", request.recentFailurePenalty(),
                "storageAvailable", request.storageAvailable(),
                "supportedTemplates", request.supportedTemplates()
            ))
            .thenApply(body -> runtimeAction(body, "HEARTBEAT_ACCEPTED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta) {
        requireId(islandId, "islandId");
        String safeMaterialKey = materialKey == null ? "" : materialKey.trim();
        if (safeMaterialKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("materialKey is required"));
        }
        return core.postWithResultBody("/v1/islands/blocks/delta", CoreJsonPayload.object("islandId", islandId, "materialKey", safeMaterialKey, "delta", delta))
            .thenApply(body -> runtimeAction(body, "BLOCK_DELTA_RECORDED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/islands/blocks/replace", CoreJsonPayload.object("islandId", islandId, "counts", CoreJsonPayload.positiveLongMap(counts)))
            .thenApply(body -> runtimeAction(body, "BLOCK_COUNTS_REPLACED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
        return core.postWithResultBody("/v1/jobs/complete", CoreJsonPayload.object("nodeId", requireJobNode(nodeId), "jobId", requireJobId(jobId), "payload", CoreJsonPayload.stringMap(payload == null ? Map.of() : payload)))
            .thenApply(body -> runtimeAction(body, "JOB_COMPLETED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage) {
        return core.postWithResultBody("/v1/jobs/fail", CoreJsonPayload.object("nodeId", requireJobNode(nodeId), "jobId", requireJobId(jobId), "error", errorMessage == null ? "" : errorMessage))
            .thenApply(body -> runtimeAction(body, "JOB_FAILED"));
    }

    public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId) {
        return completeJob(nodeId, jobId, Map.of());
    }

    private static RuntimeActionView runtimeAction(String body, String fallbackCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new RuntimeActionView(CoreJson.accepted(root), CoreJson.code(root, fallbackCode));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String requireJobNode(String nodeId) {
        String value = nodeId == null ? "" : nodeId.trim();
        if (value.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        return value;
    }

    private static UUID requireJobId(UUID jobId) {
        if (jobId == null) {
            throw new IllegalArgumentException("jobId is required");
        }
        return jobId;
    }

}
