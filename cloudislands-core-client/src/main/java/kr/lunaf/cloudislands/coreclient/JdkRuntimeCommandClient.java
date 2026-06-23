package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;
import kr.lunaf.cloudislands.protocol.job.JobClaimLease;
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
        return core.postResultBody("/v1/nodes/heartbeat", CoreJsonPayload.object(
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
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> runtimeAction(body, "HEARTBEAT_ACCEPTED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> recordBlockDelta(UUID islandId, String materialKey, long delta) {
        requireId(islandId, "islandId");
        String safeMaterialKey = materialKey == null ? "" : materialKey.trim();
        if (safeMaterialKey.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("materialKey is required"));
        }
        return core.postResultBody("/v1/islands/blocks/delta", CoreJsonPayload.object("islandId", islandId, "materialKey", safeMaterialKey, "delta", delta))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> runtimeAction(body, "BLOCK_DELTA_RECORDED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> replaceBlockCounts(UUID islandId, Map<String, Long> counts) {
        requireId(islandId, "islandId");
        return core.postResultBody("/v1/islands/blocks/replace", CoreJsonPayload.object("islandId", islandId, "counts", CoreJsonPayload.positiveLongMap(counts)))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> runtimeAction(body, "BLOCK_COUNTS_REPLACED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
        return completeJob(nodeId, jobId, JobClaimLease.unclaimed(jobId), payload);
    }

    @Override
    public CompletableFuture<RuntimeActionView> completeJob(String nodeId, UUID jobId, JobClaimLease claimLease, Map<String, String> payload) {
        return core.postResultBody("/v1/jobs/complete", CoreJsonPayload.object("nodeId", requireJobNode(nodeId), "jobId", requireJobId(jobId), "claimLease", claimLeasePayload(claimLease, jobId, nodeId), "payload", CoreJsonPayload.stringMap(payload == null ? Map.of() : payload)))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> runtimeAction(body, "JOB_COMPLETED"));
    }

    @Override
    public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, String errorMessage) {
        return failJob(nodeId, jobId, JobClaimLease.unclaimed(jobId), errorMessage);
    }

    @Override
    public CompletableFuture<RuntimeActionView> failJob(String nodeId, UUID jobId, JobClaimLease claimLease, String errorMessage) {
        return core.postResultBody("/v1/jobs/fail", CoreJsonPayload.object("nodeId", requireJobNode(nodeId), "jobId", requireJobId(jobId), "claimLease", claimLeasePayload(claimLease, jobId, nodeId), "error", errorMessage == null ? "" : errorMessage))
            .thenApply(CoreResponseBody::value)
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

    private static Map<String, Object> claimLeasePayload(JobClaimLease claimLease, UUID jobId, String nodeId) {
        if (claimLease == null || !claimLease.claimed()) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("jobId", claimLease.jobId() == null ? jobId : claimLease.jobId());
        values.put("streamId", claimLease.streamId());
        values.put("claimedByNode", claimLease.claimedByNode().isBlank() ? nodeId : claimLease.claimedByNode());
        values.put("claimToken", claimLease.claimToken());
        values.put("claimEpoch", claimLease.claimEpoch());
        values.put("leaseExpiresAt", claimLease.leaseExpiresAt().toString());
        values.put("attempt", claimLease.attempt());
        return values;
    }

}
