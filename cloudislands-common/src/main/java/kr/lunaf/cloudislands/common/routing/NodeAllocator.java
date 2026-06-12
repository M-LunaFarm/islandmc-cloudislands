package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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
        List<NodeLoad> eligible = nodes.stream()
            .filter(node -> node.inPool(pool))
            .filter(node -> node.eligible(now, heartbeatTimeout))
            .filter(node -> node.supportsTemplate(templateId))
            .filter(node -> node.satisfiesMinVersion(minNodeVersion))
            .toList();
        Optional<NodeLoad> ready = eligible.stream()
            .filter(node -> node.state() == NodeState.READY)
            .min(Comparator.comparingDouble(NodeLoad::score));
        return ready.isPresent() ? ready : eligible.stream().min(Comparator.comparingDouble(NodeLoad::score));
    }

    public Optional<NodeLoad> selectReadyNode(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        return nodes.stream()
            .filter(node -> node.inPool(pool))
            .filter(node -> newActivationBlockReason(node, now).isBlank())
            .filter(node -> node.supportsTemplate(templateId))
            .filter(node -> node.satisfiesMinVersion(minNodeVersion))
            .min(Comparator.comparingDouble(NodeLoad::score));
    }

    public String readyNodeBlockReason(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        String fallback = "NO_READY_NODE";
        boolean anyPoolNode = false;
        for (NodeLoad node : nodes) {
            if (!node.inPool(pool)) {
                continue;
            }
            anyPoolNode = true;
            if (!node.supportsTemplate(templateId)) {
                fallback = fallback.equals("NO_READY_NODE") ? "TEMPLATE_UNSUPPORTED" : fallback;
                continue;
            }
            if (!node.satisfiesMinVersion(minNodeVersion)) {
                fallback = fallback.equals("NO_READY_NODE") ? "NODE_VERSION_TOO_OLD" : fallback;
                continue;
            }
            String blockReason = newActivationBlockReason(node, now);
            if (blockReason.isBlank()) {
                return "";
            }
            fallback = fallback.equals("NO_READY_NODE") ? blockReason : fallback;
        }
        return anyPoolNode ? fallback : "POOL_EMPTY";
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
        if (!node.storageAvailable()) {
            return "STORAGE_UNAVAILABLE";
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
        if (!node.satisfiesMinVersion(minNodeVersion)) {
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

    public String existingRouteBlockReason(NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        if (node == null) {
            return "NODE_NOT_FOUND";
        }
        if (!node.inPool(pool)) {
            return "POOL_MISMATCH";
        }
        return node.existingRouteBlockReason(now, heartbeatTimeout, templateId, minNodeVersion);
    }
}
