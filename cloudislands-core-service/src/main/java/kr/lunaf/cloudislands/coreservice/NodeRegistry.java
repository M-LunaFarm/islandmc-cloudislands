package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Instant nowInstant = Instant.ofEpochMilli(now);
        Map<String, Integer> velocityServerCounts = velocityServerCounts(snapshot);
        long ready = snapshot.stream().filter(node -> allocationBlockReason(node, timeout, nowInstant, velocityServerCounts).isBlank()).count();
        long stale = snapshot.stream().filter(node -> node.lastHeartbeat() == null || node.lastHeartbeat().isBefore(nowInstant.minus(timeout))).count();
        long duplicateVelocityServers = duplicateVelocityServerNameNodes(snapshot);
        long defaultNodeIdentities = snapshot.stream().filter(NodeRegistry::defaultNodeIdentityRisk).count();
        StringBuilder builder = new StringBuilder("{")
            .append("\"nodeCount\":").append(snapshot.size()).append(',')
            .append("\"routeCandidateCount\":").append(ready).append(',')
            .append("\"staleNodeCount\":").append(stale).append(',')
            .append("\"duplicateVelocityServerNameNodeCount\":").append(duplicateVelocityServers).append(',')
            .append("\"defaultNodeIdentityRiskCount\":").append(defaultNodeIdentities).append(',')
            .append("\"heartbeatTimeoutSeconds\":").append(timeout.toSeconds()).append(',')
            .append("\"pools\":").append(poolSummaryJson(snapshot, timeout, nowInstant)).append(',')
            .append("\"nodes\":[");
        boolean first = true;
        for (NodeLoad node : snapshot) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(toJson(node, timeout, velocityServerCounts));
        }
        return builder.append("]}").toString();
    }

    private static String poolSummaryJson(List<NodeLoad> snapshot, Duration timeout, Instant now) {
        Map<String, PoolSummary> pools = new LinkedHashMap<>();
        Map<String, Integer> velocityServerCounts = velocityServerCounts(snapshot);
        for (NodeLoad node : snapshot) {
            String pool = node.pool() == null || node.pool().isBlank() ? "island" : node.pool();
            PoolSummary summary = pools.computeIfAbsent(pool, _pool -> new PoolSummary());
            summary.nodes++;
            summary.players += Math.max(0, node.players());
            summary.activeIslands += Math.max(0, node.activeIslands());
            summary.softPlayerCap += Math.max(0, node.softPlayerCap());
            summary.hardPlayerCap += Math.max(0, node.hardPlayerCap());
            summary.reservedSlots += Math.max(0, node.reservedSlots());
            summary.maxActiveIslands += Math.max(0, node.maxActiveIslands());
            summary.activationQueue += Math.max(0, node.activationQueue());
            summary.maxActivationQueue += Math.max(0, node.maxActivationQueue());
            boolean stale = node.lastHeartbeat() == null || node.lastHeartbeat().isBefore(now.minus(timeout));
            boolean routeCandidate = allocationBlockReason(node, timeout, now, velocityServerCounts).isBlank();
            if (routeCandidate) {
                summary.routeCandidates++;
            }
            if (stale) {
                summary.stale++;
            }
            if (routeCandidate && !stale) {
                summary.healthy++;
            }
            if (defaultNodeIdentityRisk(node)) {
                summary.defaultNodeIdentityRisk++;
            }
            String serverKey = velocityServerKey(node);
            if (!serverKey.isBlank() && velocityServerCounts.getOrDefault(serverKey, 0) > 1) {
                summary.duplicateVelocityServerNameNodes++;
            }
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<String, PoolSummary> entry : pools.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            PoolSummary summary = entry.getValue();
            builder.append('{')
                .append("\"pool\":\"").append(entry.getKey().replace("\"", "'")).append("\",")
                .append("\"nodeCount\":").append(summary.nodes).append(',')
                .append("\"routeCandidateCount\":").append(summary.routeCandidates).append(',')
                .append("\"healthyNodeCount\":").append(summary.healthy).append(',')
                .append("\"staleNodeCount\":").append(summary.stale).append(',')
                .append("\"players\":").append(summary.players).append(',')
                .append("\"softPlayerCap\":").append(summary.softPlayerCap).append(',')
                .append("\"hardPlayerCap\":").append(summary.hardPlayerCap).append(',')
                .append("\"reservedSlots\":").append(summary.reservedSlots).append(',')
                .append("\"activeIslands\":").append(summary.activeIslands).append(',')
                .append("\"maxActiveIslands\":").append(summary.maxActiveIslands).append(',')
                .append("\"activationQueue\":").append(summary.activationQueue).append(',')
                .append("\"maxActivationQueue\":").append(summary.maxActivationQueue).append(',')
                .append("\"duplicateVelocityServerNameNodeCount\":").append(summary.duplicateVelocityServerNameNodes).append(',')
                .append("\"defaultNodeIdentityRiskCount\":").append(summary.defaultNodeIdentityRisk)
                .append('}');
        }
        return builder.append(']').toString();
    }

    private static long duplicateVelocityServerNameNodes(List<NodeLoad> snapshot) {
        Map<String, Integer> counts = velocityServerCounts(snapshot);
        return snapshot.stream()
            .filter(node -> {
                String key = velocityServerKey(node);
                return !key.isBlank() && counts.getOrDefault(key, 0) > 1;
            })
            .count();
    }

    private static String allocationBlockReason(NodeLoad node, Duration timeout, Instant now, Map<String, Integer> velocityServerCounts) {
        String serverKey = velocityServerKey(node);
        if (!serverKey.isBlank() && velocityServerCounts.getOrDefault(serverKey, 0) > 1) {
            return "DUPLICATE_VELOCITY_SERVER_NAME";
        }
        return node.allocationBlockReason(now, timeout);
    }

    private static Map<String, Integer> velocityServerCounts(List<NodeLoad> snapshot) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (NodeLoad node : snapshot) {
            String key = velocityServerKey(node);
            if (!key.isBlank()) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private static String velocityServerKey(NodeLoad node) {
        if (node == null || node.velocityServerName() == null || node.velocityServerName().isBlank()) {
            return "";
        }
        String pool = node.pool() == null || node.pool().isBlank() ? "island" : node.pool().trim().toLowerCase(java.util.Locale.ROOT);
        return pool + "\n" + node.velocityServerName().trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean defaultNodeIdentityRisk(NodeLoad node) {
        if (node == null) {
            return false;
        }
        String nodeId = node.nodeId() == null ? "" : node.nodeId().trim();
        String serverName = node.velocityServerName() == null ? "" : node.velocityServerName().trim();
        return nodeId.equalsIgnoreCase("island-1") || serverName.equalsIgnoreCase("Island-1");
    }

    final class PoolSummary {
        private long nodes;
        private long routeCandidates;
        private long healthy;
        private long stale;
        private long players;
        private long activeIslands;
        private long softPlayerCap;
        private long hardPlayerCap;
        private long reservedSlots;
        private long maxActiveIslands;
        private long activationQueue;
        private long maxActivationQueue;
        private long duplicateVelocityServerNameNodes;
        private long defaultNodeIdentityRisk;
    }

    static String toJson(NodeLoad node) {
        return toJson(node, Duration.ofSeconds(5));
    }

    static String toJson(NodeLoad node, Duration heartbeatTimeout) {
        return toJson(node, heartbeatTimeout, velocityServerCounts(List.of(node)));
    }

    private static String toJson(NodeLoad node, Duration heartbeatTimeout, Map<String, Integer> velocityServerCounts) {
        java.util.Map<String, String> metadata = node.heartbeatMetadata();
        Duration timeout = heartbeatTimeout == null ? Duration.ofSeconds(5) : heartbeatTimeout;
        java.time.Instant now = java.time.Instant.now();
        String allocationBlockReason = allocationBlockReason(node, timeout, now, velocityServerCounts);
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
            .append("\"defaultNodeIdentityRisk\":").append(defaultNodeIdentityRisk(node)).append(',')
            .append("\"levelScan\":{")
            .append("\"running\":").append(Boolean.parseBoolean(metadata.getOrDefault("levelScanRunning", "false"))).append(',')
            .append("\"lastIsland\":\"").append(metadata.getOrDefault("lastLevelScanIsland", "").replace("\"", "'")).append("\",")
            .append("\"startedAt\":").append(longMetadata(metadata, "lastLevelScanStartedAt")).append(',')
            .append("\"finishedAt\":").append(longMetadata(metadata, "lastLevelScanFinishedAt")).append(',')
            .append("\"failedAt\":").append(longMetadata(metadata, "lastLevelScanFailedAt"))
            .append("},")
            .append("\"storage\":{")
            .append("\"backend\":\"").append(metadata.getOrDefault("storageBackend", "").replace("\"", "'")).append("\",")
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
            .append(",\"scoreBreakdown\":").append(scoreBreakdownJson(node.scoreBreakdown()))
            .append('}')
            .toString();
    }

    private static String scoreBreakdownJson(java.util.Map<String, Double> breakdown) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (java.util.Map.Entry<String, Double> entry : breakdown.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(entry.getKey().replace("\"", "'")).append("\":").append(entry.getValue());
        }
        return builder.append('}').toString();
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
