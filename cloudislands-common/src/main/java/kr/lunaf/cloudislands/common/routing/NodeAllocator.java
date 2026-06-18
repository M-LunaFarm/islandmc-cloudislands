package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import kr.lunaf.cloudislands.api.model.NodeState;

public final class NodeAllocator {
    private final Duration heartbeatTimeout;
    private final boolean avoidSoftFullNewActivations;
    private final boolean allowHardFullNewActivations;

    public NodeAllocator(Duration heartbeatTimeout) {
        this(heartbeatTimeout, "AVOID_NEW_ACTIVATIONS");
    }

    public NodeAllocator(Duration heartbeatTimeout, String softFullPolicy) {
        this(heartbeatTimeout, softFullPolicy, "DENY_OR_QUEUE");
    }

    public NodeAllocator(Duration heartbeatTimeout, String softFullPolicy, String hardFullPolicy) {
        this.heartbeatTimeout = heartbeatTimeout;
        this.avoidSoftFullNewActivations = softFullPolicy == null
            || softFullPolicy.isBlank()
            || softFullPolicy.equalsIgnoreCase("AVOID_NEW_ACTIVATIONS")
            || softFullPolicy.equalsIgnoreCase("READY_ONLY");
        this.allowHardFullNewActivations = hardFullPolicy != null
            && (hardFullPolicy.equalsIgnoreCase("ALLOW_NEW_ACTIVATIONS")
                || hardFullPolicy.equalsIgnoreCase("ALLOW")
                || hardFullPolicy.equalsIgnoreCase("IGNORE_HARD_FULL"));
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now) {
        return selectBestNode(nodes, now, null);
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId) {
        return selectBestNode(nodes, now, templateId, "");
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion) {
        return selectBestNode(nodes, now, templateId, minNodeVersion, "island");
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        List<NodeLoad> safeNodes = safeNodes(nodes);
        Map<String, Integer> velocityServerCounts = velocityServerCounts(safeNodes);
        List<NodeLoad> eligible = safeNodes.stream()
            .filter(node -> node.inPool(pool))
            .filter(node -> !duplicateVelocityServerName(node, velocityServerCounts))
            .filter(node -> node.eligible(now, heartbeatTimeout))
            .filter(node -> node.supportsTemplate(templateId))
            .filter(node -> node.satisfiesTemplateVersion(templateId, minNodeVersion))
            .toList();
        Optional<NodeLoad> ready = eligible.stream()
            .filter(node -> node.state() == NodeState.READY)
            .min(allocationComparator());
        return ready.isPresent() ? ready : eligible.stream().min(allocationComparator());
    }

    public Optional<NodeLoad> selectReadyNode(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        return readyNodeCandidates(nodes, now, templateId, minNodeVersion, pool).stream()
            .min(allocationComparator());
    }

    public long readyNodeCandidateCount(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        return readyNodeCandidates(nodes, now, templateId, minNodeVersion, pool).size();
    }

    private List<NodeLoad> readyNodeCandidates(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        List<NodeLoad> safeNodes = safeNodes(nodes);
        Map<String, Integer> velocityServerCounts = velocityServerCounts(safeNodes);
        return safeNodes.stream()
            .filter(node -> node.inPool(pool))
            .filter(node -> !duplicateVelocityServerName(node, velocityServerCounts))
            .filter(node -> newActivationBlockReason(node, now).isBlank())
            .filter(node -> node.supportsTemplate(templateId))
            .filter(node -> node.satisfiesTemplateVersion(templateId, minNodeVersion))
            .toList();
    }

    public Optional<NodeLoad> selectTargetNode(List<NodeLoad> nodes, Instant now, String targetNodeId, String templateId, String minNodeVersion, String pool) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return Optional.empty();
        }
        List<NodeLoad> safeNodes = safeNodes(nodes);
        Map<String, Integer> velocityServerCounts = velocityServerCounts(safeNodes);
        return safeNodes.stream()
            .filter(node -> targetNodeId.equals(node.nodeId()))
            .filter(node -> !duplicateVelocityServerName(node, velocityServerCounts))
            .filter(node -> nodeBlockReason(node, now, templateId, minNodeVersion, pool).isBlank())
            .findFirst();
    }

    public String targetNodeBlockReason(List<NodeLoad> nodes, Instant now, String targetNodeId, String templateId, String minNodeVersion, String pool) {
        if (targetNodeId == null || targetNodeId.isBlank()) {
            return "NODE_NOT_FOUND";
        }
        List<NodeLoad> safeNodes = safeNodes(nodes);
        Map<String, Integer> velocityServerCounts = velocityServerCounts(safeNodes);
        for (NodeLoad node : safeNodes) {
            if (!targetNodeId.equals(node.nodeId())) {
                continue;
            }
            if (duplicateVelocityServerName(node, velocityServerCounts)) {
                return "DUPLICATE_VELOCITY_SERVER_NAME";
            }
            return nodeBlockReason(node, now, templateId, minNodeVersion, pool);
        }
        return "NODE_NOT_FOUND";
    }

    public String readyNodeBlockReason(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        boolean anyPoolNode = false;
        boolean anyTemplateNode = false;
        boolean anyVersionNode = false;
        String allocationFallback = "NO_READY_NODE";
        List<NodeLoad> safeNodes = safeNodes(nodes);
        Map<String, Integer> velocityServerCounts = velocityServerCounts(safeNodes);
        for (NodeLoad node : safeNodes) {
            if (!node.inPool(pool)) {
                continue;
            }
            anyPoolNode = true;
            if (!node.supportsTemplate(templateId)) {
                continue;
            }
            anyTemplateNode = true;
            if (!node.satisfiesTemplateVersion(templateId, minNodeVersion)) {
                continue;
            }
            anyVersionNode = true;
            if (duplicateVelocityServerName(node, velocityServerCounts)) {
                allocationFallback = preferredBlockReason(allocationFallback, "DUPLICATE_VELOCITY_SERVER_NAME");
                continue;
            }
            String blockReason = newActivationBlockReason(node, now);
            if (blockReason.isBlank()) {
                return "";
            }
            allocationFallback = preferredBlockReason(allocationFallback, blockReason);
        }
        if (!anyPoolNode) {
            return "POOL_EMPTY";
        }
        if (!anyTemplateNode) {
            return "TEMPLATE_UNSUPPORTED";
        }
        if (!anyVersionNode) {
            return "NODE_VERSION_TOO_OLD";
        }
        return allocationFallback;
    }

    private String newActivationBlockReason(NodeLoad node, Instant now) {
        if (node.state() == NodeState.READY) {
            return sharedNewActivationBlockReason(node, now, false);
        }
        if (node.state() == NodeState.SOFT_FULL) {
            return avoidSoftFullNewActivations ? "STATE_SOFT_FULL" : sharedNewActivationBlockReason(node, now, false);
        }
        if (node.state() == NodeState.HARD_FULL) {
            return allowHardFullNewActivations ? sharedNewActivationBlockReason(node, now, true) : "STATE_HARD_FULL";
        }
        return "STATE_" + node.state().name();
    }

    private String sharedNewActivationBlockReason(NodeLoad node, Instant now, boolean ignoreHardPlayerCap) {
        if (node.defaultNodeIdentityRisk()) {
            return "DEFAULT_NODE_IDENTITY";
        }
        if (!node.storageAvailable()) {
            return "STORAGE_UNAVAILABLE";
        }
        if (node.storagePrimaryDegraded()) {
            return "STORAGE_PRIMARY_DEGRADED";
        }
        if (node.storageSaveRetryQueueTotal() > 0) {
            return "STORAGE_SAVE_RETRY_QUEUE";
        }
        if (node.lastHeartbeat() == null) {
            return "HEARTBEAT_MISSING";
        }
        if (node.lastHeartbeat().plus(heartbeatTimeout).isBefore(now)) {
            return "HEARTBEAT_STALE";
        }
        if (!ignoreHardPlayerCap && node.hardPlayerCap() > 0 && node.players() >= node.hardPlayerCap()) {
            return "HARD_PLAYER_CAP";
        }
        if (node.maxActiveIslands() > 0 && node.activeIslands() >= node.maxActiveIslands()) {
            return "MAX_ACTIVE_ISLANDS";
        }
        if (node.maxActivationQueue() > 0 && node.activationQueue() >= node.maxActivationQueue()) {
            return "MAX_ACTIVATION_QUEUE";
        }
        return "";
    }

    private String preferredBlockReason(String current, String next) {
        if (current == null || current.isBlank() || current.equals("NO_READY_NODE")) {
            return next;
        }
        if (next == null || next.isBlank()) {
            return current;
        }
        return blockReasonPriority(next) < blockReasonPriority(current) ? next : current;
    }

    private Comparator<NodeLoad> allocationComparator() {
        return Comparator.comparingDouble(NodeLoad::score)
            .thenComparingInt(NodeLoad::activationQueue)
            .thenComparingInt(NodeLoad::activeIslands)
            .thenComparingInt(NodeLoad::players)
            .thenComparing(node -> node.nodeId() == null ? "" : node.nodeId());
    }

    private int blockReasonPriority(String reason) {
        if (reason == null || reason.isBlank()) {
            return 100;
        }
        if (reason.equals("HEARTBEAT_MISSING") || reason.equals("HEARTBEAT_STALE")) {
            return 10;
        }
        if (reason.equals("STORAGE_UNAVAILABLE")) {
            return 20;
        }
        if (reason.equals("STORAGE_PRIMARY_DEGRADED")) {
            return 21;
        }
        if (reason.equals("STORAGE_SAVE_RETRY_QUEUE")) {
            return 22;
        }
        if (reason.equals("DUPLICATE_VELOCITY_SERVER_NAME")) {
            return 25;
        }
        if (reason.equals("DEFAULT_NODE_IDENTITY")) {
            return 26;
        }
        if (reason.equals("MAX_ACTIVATION_QUEUE")) {
            return 30;
        }
        if (reason.equals("HARD_PLAYER_CAP") || reason.equals("STATE_HARD_FULL")) {
            return 40;
        }
        if (reason.equals("MAX_ACTIVE_ISLANDS")) {
            return 50;
        }
        if (reason.equals("STATE_SOFT_FULL")) {
            return 60;
        }
        if (reason.startsWith("STATE_")) {
            return 70;
        }
        return 80;
    }

    public String nodeBlockReason(NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        if (node == null) {
            return "NODE_NOT_FOUND";
        }
        if (!node.inPool(pool)) {
            return "POOL_MISMATCH";
        }
        if (!node.supportsTemplate(templateId)) {
            return "TEMPLATE_UNSUPPORTED";
        }
        if (!node.satisfiesTemplateVersion(templateId, minNodeVersion)) {
            return "NODE_VERSION_TOO_OLD";
        }
        return node.allocationBlockReason(now, heartbeatTimeout);
    }

    public boolean acceptsExistingRoute(NodeLoad node, Instant now, String templateId, String minNodeVersion) {
        return acceptsExistingRoute(node, now, templateId, minNodeVersion, "island");
    }

    public boolean acceptsExistingRoute(NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        return node.inPool(pool) && node.acceptsExistingRoute(now, heartbeatTimeout, templateId, minNodeVersion);
    }

    public boolean acceptsExistingRoute(List<NodeLoad> nodes, NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        return existingRouteBlockReason(nodes, node, now, templateId, minNodeVersion, pool).isBlank();
    }

    public String existingRouteBlockReason(NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        if (node == null) {
            return "NODE_NOT_FOUND";
        }
        if (!node.inPool(pool)) {
            return "POOL_MISMATCH";
        }
        return node.existingRouteBlockReason(now, heartbeatTimeout, templateId, minNodeVersion);
    }

    public String existingRouteBlockReason(List<NodeLoad> nodes, NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        if (node == null) {
            return "NODE_NOT_FOUND";
        }
        Map<String, Integer> velocityServerCounts = velocityServerCounts(nodes == null ? List.of() : nodes);
        if (duplicateVelocityServerName(node, velocityServerCounts)) {
            return "DUPLICATE_VELOCITY_SERVER_NAME";
        }
        return existingRouteBlockReason(node, now, templateId, minNodeVersion, pool);
    }

    private Map<String, Integer> velocityServerCounts(List<NodeLoad> nodes) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (NodeLoad node : safeNodes(nodes)) {
            String key = velocityServerKey(node);
            if (!key.isBlank()) {
                counts.merge(key, 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<NodeLoad> safeNodes(List<NodeLoad> nodes) {
        return nodes == null ? List.of() : nodes;
    }

    private boolean duplicateVelocityServerName(NodeLoad node, Map<String, Integer> counts) {
        String key = velocityServerKey(node);
        return !key.isBlank() && counts.getOrDefault(key, 0) > 1;
    }

    private String velocityServerKey(NodeLoad node) {
        if (node == null || node.velocityServerName() == null || node.velocityServerName().isBlank()) {
            return "";
        }
        String pool = node.pool() == null || node.pool().isBlank() ? "island" : node.pool().trim().toLowerCase(Locale.ROOT);
        return pool + "\n" + node.velocityServerName().trim().toLowerCase(Locale.ROOT);
    }
}
