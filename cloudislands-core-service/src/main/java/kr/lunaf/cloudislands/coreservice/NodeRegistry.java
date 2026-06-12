package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public interface NodeRegistry {
    void heartbeat(NodeHeartbeatRequest request);

    boolean drain(String nodeId);

    boolean undrain(String nodeId);

    List<String> markStaleDown(Duration heartbeatTimeout);

    List<NodeLoad> snapshot();

    Optional<NodeLoad> find(String nodeId);

    default String toJson() {
        StringBuilder builder = new StringBuilder("{\"nodes\":[");
        boolean first = true;
        for (NodeLoad node : snapshot()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(toJson(node));
        }
        return builder.append("]}").toString();
    }

    static String toJson(NodeLoad node) {
        java.util.Map<String, String> metadata = node.heartbeatMetadata();
        return new StringBuilder("{")
            .append("\"id\":\"").append(node.nodeId()).append("\",")
            .append("\"pool\":\"").append(node.pool() == null ? "island" : node.pool()).append("\",")
            .append("\"server\":\"").append(node.velocityServerName()).append("\",")
            .append("\"nodeVersion\":\"").append(node.nodeVersion() == null ? "" : node.nodeVersion().replace("\"", "'")).append("\",")
            .append("\"state\":\"").append(node.state()).append("\",")
            .append("\"players\":").append(node.players()).append(',')
            .append("\"softPlayerCap\":").append(node.softPlayerCap()).append(',')
            .append("\"hardPlayerCap\":").append(node.hardPlayerCap()).append(',')
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
            .append("\"lastHeartbeat\":\"").append(node.lastHeartbeat()).append("\",")
            .append("\"score\":").append(node.score())
            .append(",\"scoreBreakdown\":{")
            .append("\"playerPressure\":").append(node.playerPressure()).append(',')
            .append("\"activeIslandPressure\":").append(node.activeIslandPressure()).append(',')
            .append("\"msptPressure\":").append(node.msptPressure()).append(',')
            .append("\"activationQueuePressure\":").append(node.activationQueuePressure()).append(',')
            .append("\"chunkLoadPressure\":").append(node.chunkLoadPressure()).append(',')
            .append("\"memoryPressure\":").append(node.memoryPressure()).append(',')
            .append("\"recentFailurePenalty\":").append(node.recentFailurePenalty())
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
}
