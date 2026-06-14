package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public interface NodeRegistry {
    void heartbeat(NodeHeartbeatRequest request);

    boolean drain(String nodeId);

    boolean shutdownSafe(String nodeId);

    boolean undrain(String nodeId);

    List<String> markStaleDown(Duration heartbeatTimeout);

    List<NodeLoad> snapshot();

    Optional<NodeLoad> find(String nodeId);

    static NodeState normalizeHeartbeatState(NodeHeartbeatRequest request, NodeState currentState) {
        if (currentState == NodeState.DRAINING || currentState == NodeState.SHUTTING_DOWN) {
            return currentState;
        }
        NodeState requested = request.state();
        if (requested != NodeState.READY && requested != NodeState.SOFT_FULL) {
            return requested;
        }
        if (request.hardPlayerCap() > 0 && request.players() >= request.hardPlayerCap()) {
            return NodeState.HARD_FULL;
        }
        if (request.maxActiveIslands() > 0 && request.activeIslands() >= request.maxActiveIslands()) {
            return NodeState.HARD_FULL;
        }
        if (request.maxActivationQueue() > 0 && request.activationQueue() >= request.maxActivationQueue()) {
            return NodeState.HARD_FULL;
        }
        if (request.softPlayerCap() > 0 && request.players() >= request.softPlayerCap()) {
            return NodeState.SOFT_FULL;
        }
        return NodeState.READY;
    }

    default String toJson() {
        return toJson(Duration.ofSeconds(5));
    }

    default String toJson(Duration heartbeatTimeout) {
        Duration timeout = heartbeatTimeout == null ? Duration.ofSeconds(5) : heartbeatTimeout;
        List<NodeLoad> snapshot = snapshot();
        long now = System.currentTimeMillis();
        long ready = snapshot.stream().filter(node -> node.allocationBlockReason(java.time.Instant.ofEpochMilli(now), timeout).isBlank()).count();
        long stale = snapshot.stream().filter(node -> node.lastHeartbeat() == null || node.lastHeartbeat().isBefore(java.time.Instant.ofEpochMilli(now).minus(timeout))).count();
        StringBuilder builder = new StringBuilder("{")
            .append("\"nodeCount\":").append(snapshot.size()).append(',')
            .append("\"routeCandidateCount\":").append(ready).append(',')
            .append("\"staleNodeCount\":").append(stale).append(',')
            .append("\"heartbeatTimeoutSeconds\":").append(timeout.toSeconds()).append(',')
            .append("\"nodes\":[");
        boolean first = true;
        for (NodeLoad node : snapshot) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(toJson(node, timeout));
        }
        return builder.append("]}").toString();
    }

    static String toJson(NodeLoad node) {
        return toJson(node, Duration.ofSeconds(5));
    }

    static String toJson(NodeLoad node, Duration heartbeatTimeout) {
        java.util.Map<String, String> metadata = node.heartbeatMetadata();
        Duration timeout = heartbeatTimeout == null ? Duration.ofSeconds(5) : heartbeatTimeout;
        java.time.Instant now = java.time.Instant.now();
        String allocationBlockReason = node.allocationBlockReason(now, timeout);
        long secondsSinceHeartbeat = node.lastHeartbeat() == null ? -1L : Math.max(0L, Duration.between(node.lastHeartbeat(), now).toSeconds());
        boolean stale = node.lastHeartbeat() == null || node.lastHeartbeat().isBefore(now.minus(timeout));
        boolean routeCandidate = allocationBlockReason.isBlank();
        boolean healthy = routeCandidate && !stale;
        return new StringBuilder("{")
            .append("\"id\":\"").append(node.nodeId()).append("\",")
            .append("\"pool\":\"").append(node.pool() == null ? "island" : node.pool()).append("\",")
            .append("\"server\":\"").append(node.velocityServerName()).append("\",")
            .append("\"nodeVersion\":\"").append(node.nodeVersion() == null ? "" : node.nodeVersion().replace("\"", "'")).append("\",")
            .append("\"state\":\"").append(node.state()).append("\",")
            .append("\"healthy\":").append(healthy).append(',')
            .append("\"routeCandidate\":").append(routeCandidate).append(',')
            .append("\"stale\":").append(stale).append(',')
            .append("\"secondsSinceHeartbeat\":").append(secondsSinceHeartbeat).append(',')
            .append("\"players\":").append(node.players()).append(',')
            .append("\"softPlayerCap\":").append(node.softPlayerCap()).append(',')
            .append("\"hardPlayerCap\":").append(node.hardPlayerCap()).append(',')
            .append("\"reservedSlots\":").append(node.reservedSlots()).append(',')
            .append("\"activeIslands\":").append(node.activeIslands()).append(',')
            .append("\"maxActiveIslands\":").append(node.maxActiveIslands()).append(',')
            .append("\"mspt\":").append(node.mspt()).append(',')
            .append("\"activationQueue\":").append(node.activationQueue()).append(',')
            .append("\"maxActivationQueue\":").append(node.maxActivationQueue()).append(',')
            .append("\"chunkLoadPressure\":").append(node.chunkLoadPressure()).append(',')
            .append("\"heapUsedMb\":").append(node.heapUsedMb()).append(',')
            .append("\"heapMaxMb\":").append(node.heapMaxMb()).append(',')
            .append("\"recentFailurePenalty\":").append(node.recentFailurePenalty()).append(',')
            .append("\"storageAvailable\":").append(node.storageAvailable()).append(',')
            .append("\"supportedTemplates\":\"").append(node.templateList() == null ? "*" : node.templateList().replace("\"", "'")).append("\",")
            .append("\"levelScan\":{")
            .append("\"running\":").append(Boolean.parseBoolean(metadata.getOrDefault("levelScanRunning", "false"))).append(',')
            .append("\"lastIsland\":\"").append(metadata.getOrDefault("lastLevelScanIsland", "").replace("\"", "'")).append("\",")
            .append("\"startedAt\":").append(longMetadata(metadata, "lastLevelScanStartedAt")).append(',')
            .append("\"finishedAt\":").append(longMetadata(metadata, "lastLevelScanFinishedAt")).append(',')
            .append("\"failedAt\":").append(longMetadata(metadata, "lastLevelScanFailedAt"))
            .append("},")
            .append("\"storage\":{")
            .append("\"uploadSeconds\":").append(doubleMetadata(metadata, "storageUploadSeconds")).append(',')
            .append("\"downloadSeconds\":").append(doubleMetadata(metadata, "storageDownloadSeconds")).append(',')
            .append("\"healthCheckFailures\":").append(longMetadata(metadata, "storageHealthCheckFailures")).append(',')
            .append("\"uploadFailures\":").append(longMetadata(metadata, "storageUploadFailures")).append(',')
            .append("\"downloadFailures\":").append(longMetadata(metadata, "storageDownloadFailures")).append(',')
            .append("\"operationFailures\":").append(longMetadata(metadata, "storageOperationFailures"))
            .append("},")
            .append("\"lastHeartbeat\":\"").append(node.lastHeartbeat()).append("\",")
            .append("\"eligibleForNewActivation\":").append(allocationBlockReason.isBlank()).append(',')
            .append("\"allocationBlockReason\":\"").append(allocationBlockReason).append("\",")
            .append("\"score\":").append(node.score())
            .append(",\"scoreBreakdown\":{")
            .append("\"playerPressure\":").append(node.playerPressure()).append(',')
            .append("\"activeIslandPressure\":").append(node.activeIslandPressure()).append(',')
            .append("\"msptPressure\":").append(node.msptPressure()).append(',')
            .append("\"activationQueuePressure\":").append(node.activationQueuePressure()).append(',')
            .append("\"chunkLoadPressure\":").append(node.chunkLoadPressure()).append(',')
            .append("\"memoryPressure\":").append(node.memoryPressure()).append(',')
            .append("\"recentFailurePenalty\":").append(node.recentFailurePenalty()).append(',')
            .append("\"recentFailurePressure\":").append(node.recentFailurePressure())
            .append('}')
            .append('}')
            .toString();
    }

    private static long longMetadata(java.util.Map<String, String> metadata, String key) {
        try {
            return Long.parseLong(metadata.getOrDefault(key, "0"));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static double doubleMetadata(java.util.Map<String, String> metadata, String key) {
        try {
            return Double.parseDouble(metadata.getOrDefault(key, "0.0"));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }
}
