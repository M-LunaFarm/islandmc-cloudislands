package kr.lunaf.cloudislands.coreservice.config;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.coreservice.NodeRegistry;

public final class CoreIslandPoolSummary {
    private CoreIslandPoolSummary() {
    }

    public static long islandPoolNodeCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        return nodes.snapshot().stream()
            .filter(node -> inIslandPool(config, node))
            .count();
    }

    public static long islandPoolRouteCandidateCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        List<NodeLoad> snapshot = nodes.snapshot();
        Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        Instant now = Instant.now();
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .filter(node -> islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts).isBlank())
            .count();
    }

    public static String islandPoolRouteCandidateNodeIds(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return "";
        }
        List<NodeLoad> snapshot = nodes.snapshot();
        Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        Instant now = Instant.now();
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .filter(node -> islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts).isBlank())
            .map(NodeLoad::nodeId)
            .filter(id -> id != null && !id.isBlank())
            .sorted()
            .collect(Collectors.joining(","));
    }

    public static String islandPoolScaleStatus(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        long candidates = islandPoolRouteCandidateCount(config, nodes);
        if (nodeCount <= 0L) {
            return "NO_POOL_NODES";
        }
        if (nodeCount == 1L) {
            return candidates > 0L ? "SINGLE_NODE_READY" : "SINGLE_NODE_BLOCKED";
        }
        if (candidates <= 0L) {
            return "MULTI_NODE_BLOCKED";
        }
        if (candidates == 1L) {
            return "MULTI_NODE_DEGRADED";
        }
        return "MULTI_NODE_READY";
    }

    public static boolean islandPoolDegraded(CoreServiceConfig config, NodeRegistry nodes) {
        return islandPoolNodeCount(config, nodes) > 1L && islandPoolRouteCandidateCount(config, nodes) == 1L;
    }

    public static boolean islandPoolMultiNodeReady(CoreServiceConfig config, NodeRegistry nodes) {
        return islandPoolNodeCount(config, nodes) > 1L
            && islandPoolRouteCandidateCount(config, nodes) > 1L
            && islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) == 0L
            && islandPoolDefaultNodeIdentityRiskCount(config, nodes) == 0L;
    }

    public static long islandPoolRouteCandidateRecommendedMinimum(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        if (nodeCount <= 0L) {
            return 0L;
        }
        if (nodeCount == 1L) {
            return 1L;
        }
        if (nodeCount < 5L) {
            return 2L;
        }
        if (nodeCount <= 6L) {
            return nodeCount;
        }
        return Math.min(nodeCount, 6L);
    }

    public static String islandPoolRouteCandidateMinimumStatus(CoreServiceConfig config, NodeRegistry nodes) {
        long minimum = islandPoolRouteCandidateRecommendedMinimum(config, nodes);
        long candidates = islandPoolRouteCandidateCount(config, nodes);
        if (minimum <= 0L) {
            return "NO_POOL_NODES";
        }
        if (candidates >= minimum) {
            return "OK candidates=" + candidates + "/" + minimum;
        }
        return "SHORTFALL candidates=" + candidates + "/" + minimum
            + " blocked=" + islandPoolBlockedNodeIds(config, nodes);
    }

    public static boolean islandPoolFiveSixNodeHealthy(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        if (nodeCount < 5L || nodeCount > 6L) {
            return false;
        }
        return islandPoolRouteCandidateCount(config, nodes) >= islandPoolRouteCandidateRecommendedMinimum(config, nodes)
            && islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) == 0L
            && islandPoolDefaultNodeIdentityRiskCount(config, nodes) == 0L;
    }

    public static String islandPoolFiveSixNodeStatus(CoreServiceConfig config, NodeRegistry nodes) {
        long nodeCount = islandPoolNodeCount(config, nodes);
        long candidates = islandPoolRouteCandidateCount(config, nodes);
        if (nodeCount < 5L) {
            return "NOT_5_6_NODE_POOL";
        }
        if ((nodeCount == 5L || nodeCount == 6L)
                && candidates == nodeCount
                && islandPoolDuplicateVelocityServerNameNodeCount(config, nodes) == 0L
                && islandPoolDefaultNodeIdentityRiskCount(config, nodes) == 0L) {
            return "READY";
        }
        if (nodeCount > 6L && candidates == nodeCount) {
            return "READY_ABOVE_6_NODES";
        }
        return "DEGRADED candidates=" + candidates + "/" + nodeCount
            + " blocked=" + islandPoolBlockedNodeIds(config, nodes);
    }

    public static long islandPoolRouteCandidateShortfall(CoreServiceConfig config, NodeRegistry nodes) {
        return Math.max(0L, islandPoolNodeCount(config, nodes) - islandPoolRouteCandidateCount(config, nodes));
    }

    public static String islandPoolRouteCandidateBlockSummary(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return "none";
        }
        List<NodeLoad> snapshot = nodes.snapshot();
        Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        Instant now = Instant.now();
        Map<String, Long> blocked = new TreeMap<>();
        snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(node -> islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts))
            .filter(reason -> reason != null && !reason.isBlank())
            .forEach(reason -> blocked.merge(reason, 1L, Long::sum));
        if (blocked.isEmpty()) {
            return "none";
        }
        return blocked.entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining(","));
    }

    public static String globalEventTypeKeys() {
        return Arrays.stream(CloudIslandEventType.values())
            .map(Enum::name)
            .collect(Collectors.joining(","));
    }

    public static String islandPoolBlockedNodeIds(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return "";
        }
        List<NodeLoad> snapshot = nodes.snapshot();
        Map<String, Long> velocityServerCounts = islandPoolVelocityServerCounts(config, snapshot);
        Instant now = Instant.now();
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(node -> {
                String reason = islandPoolRouteCandidateBlockReason(config, node, now, velocityServerCounts);
                if (reason == null || reason.isBlank()) {
                    return "";
                }
                String id = node.nodeId() == null || node.nodeId().isBlank() ? "unknown" : node.nodeId();
                return id + ":" + reason;
            })
            .filter(value -> !value.isBlank())
            .sorted()
            .collect(Collectors.joining(","));
    }

    public static long islandPoolDuplicateVelocityServerNameNodeCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        List<NodeLoad> snapshot = nodes.snapshot();
        Map<String, Long> serverCounts = islandPoolVelocityServerCounts(config, snapshot);
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(CoreIslandPoolSummary::velocityServerNameKey)
            .filter(server -> !server.isBlank() && serverCounts.getOrDefault(server, 0L) > 1L)
            .count();
    }

    public static long islandPoolDefaultNodeIdentityRiskCount(CoreServiceConfig config, NodeRegistry nodes) {
        if (nodes == null) {
            return 0L;
        }
        return nodes.snapshot().stream()
            .filter(node -> inIslandPool(config, node))
            .filter(NodeLoad::defaultNodeIdentityRisk)
            .count();
    }

    private static boolean inIslandPool(CoreServiceConfig config, NodeLoad node) {
        return node != null && node.inPool(islandPool(config));
    }

    private static String islandPool(CoreServiceConfig config) {
        return config == null || config.islandPool() == null || config.islandPool().isBlank() ? "island" : config.islandPool();
    }

    private static Map<String, Long> islandPoolVelocityServerCounts(CoreServiceConfig config, List<NodeLoad> snapshot) {
        return snapshot.stream()
            .filter(node -> inIslandPool(config, node))
            .map(CoreIslandPoolSummary::velocityServerNameKey)
            .filter(server -> !server.isBlank())
            .collect(Collectors.groupingBy(server -> server, Collectors.counting()));
    }

    private static String islandPoolRouteCandidateBlockReason(CoreServiceConfig config, NodeLoad node, Instant now, Map<String, Long> velocityServerCounts) {
        String blockReason = node.allocationBlockReason(now, config.heartbeatTimeout());
        if (!blockReason.isBlank()) {
            return blockReason;
        }
        String server = velocityServerNameKey(node);
        if (!server.isBlank() && velocityServerCounts.getOrDefault(server, 0L) > 1L) {
            return "DUPLICATE_VELOCITY_SERVER_NAME";
        }
        return "";
    }

    private static String velocityServerNameKey(NodeLoad node) {
        return node == null || node.velocityServerName() == null ? "" : node.velocityServerName().trim().toLowerCase(Locale.ROOT);
    }
}
