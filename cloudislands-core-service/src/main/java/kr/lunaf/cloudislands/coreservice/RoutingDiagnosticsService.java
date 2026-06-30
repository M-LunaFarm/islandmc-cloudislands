package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.island.IslandPortabilityPolicy;
import kr.lunaf.cloudislands.common.routing.NodeAllocator;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class RoutingDiagnosticsService {
    private final NodeRegistry nodes;
    private final NodeAllocator allocator;
    private final String islandPool;

    public RoutingDiagnosticsService(NodeRegistry nodes, NodeAllocator allocator, String islandPool) {
        this.nodes = nodes;
        this.allocator = allocator;
        this.islandPool = islandPool == null || islandPool.isBlank() ? "island" : islandPool;
    }

    public Map<String, String> routingFailureDetails(String debugReason) {
        List<NodeLoad> snapshot = nodes.snapshot();
        List<NodeLoad> poolSnapshot = snapshot.stream().filter(node -> node.inPool(islandPool)).toList();
        long poolNodes = poolSnapshot.size();
        long routeCandidateEstimate = allocator.readyNodeCandidateCount(snapshot, Instant.now(), "", "", islandPool);
        long recommendedCandidates = IslandPortabilityPolicy.recommendedRouteCandidateMinimum(poolNodes);
        Map<String, String> details = new LinkedHashMap<>();
        details.put("pool", islandPool);
        details.put("nodeCount", Long.toString(poolNodes));
        details.put("readyOrSoftFullNodeCount", Long.toString(poolSnapshot.stream().filter(node -> node.state() == NodeState.READY || node.state() == NodeState.SOFT_FULL).count()));
        details.put("storageReadyNodeCount", Long.toString(poolSnapshot.stream().filter(NodeLoad::storageAvailable).count()));
        details.put("primaryStorageHealthyNodeCount", Long.toString(poolSnapshot.stream().filter(node -> !node.storagePrimaryDegraded()).count()));
        details.put("storageSaveRetryBacklogNodeCount", Long.toString(poolSnapshot.stream().filter(node -> node.storageSaveRetryQueueTotal() > 0).count()));
        details.put("storageSaveRetryBacklogTotal", Long.toString(poolSnapshot.stream().mapToLong(NodeLoad::storageSaveRetryQueueTotal).sum()));
        details.put("hardCapOpenNodeCount", Long.toString(poolSnapshot.stream().filter(node -> node.hardPlayerCap() <= 0 || node.players() < node.hardPlayerCap()).count()));
        details.put("activeIslandOpenNodeCount", Long.toString(poolSnapshot.stream().filter(node -> node.maxActiveIslands() <= 0 || node.activeIslands() < node.maxActiveIslands()).count()));
        details.put("queueOpenNodeCount", Long.toString(poolSnapshot.stream().filter(node -> node.maxActivationQueue() <= 0 || node.activationQueue() < node.maxActivationQueue()).count()));
        details.put("defaultIdentityRiskNodeCount", Long.toString(poolSnapshot.stream().filter(NodeLoad::defaultNodeIdentityRisk).count()));
        details.put("duplicateVelocityServerNameNodeCount", Long.toString(duplicateVelocityServerNameCount(poolSnapshot)));
        details.put("routeCandidateEstimateNodeCount", Long.toString(routeCandidateEstimate));
        details.put("routeCandidateRecommendedMinimum", Long.toString(recommendedCandidates));
        details.put("routeCandidateShortfall", Long.toString(Math.max(0L, recommendedCandidates - routeCandidateEstimate)));
        details.put("routeCandidateEstimatePolicy", "allocator-ready-node-candidates-no-fixed-node-limit");
        details.put("routeCandidateHardRulePolicy", NodeAllocator.ACTIVATION_HARD_RULE_POLICY);
        details.put("routeCandidateScoreWeightPolicy", NodeAllocator.SCORE_WEIGHT_POLICY);
        details.put("routeCandidateSoftFullPolicy", NodeAllocator.SOFT_FULL_NEW_ACTIVATION_POLICY);
        details.put("elasticLimitPolicy", IslandPortabilityPolicy.NO_FIXED_NODE_COUNT_LIMIT_POLICY);
        details.put("eightPlusNodePolicy", IslandPortabilityPolicy.EIGHT_PLUS_NODE_POLICY);
        details.put("routeCandidateMinimumPolicy", IslandPortabilityPolicy.ROUTE_CANDIDATE_MINIMUM_POLICY);
        details.put("routeCandidateCapLimitsNodeCount", Boolean.toString(IslandPortabilityPolicy.routeCandidateRecommendationCapsNodeCount()));
        details.put("routeCandidateCapMeaning", IslandPortabilityPolicy.routeCandidateCapMeaning(poolNodes));
        details.put("blockReason", publicBlockReason(debugReason));
        details.put("physicalNodeNamesExposed", "false");
        return Map.copyOf(details);
    }

    private long duplicateVelocityServerNameCount(List<NodeLoad> poolSnapshot) {
        Map<String, Integer> names = new LinkedHashMap<>();
        for (NodeLoad node : poolSnapshot) {
            String serverName = node.velocityServerName() == null ? "" : node.velocityServerName().trim().toLowerCase();
            if (!serverName.isBlank()) {
                names.put(serverName, names.getOrDefault(serverName, 0) + 1);
            }
        }
        return poolSnapshot.stream()
            .filter(node -> {
                String serverName = node.velocityServerName() == null ? "" : node.velocityServerName().trim().toLowerCase();
                return !serverName.isBlank() && names.getOrDefault(serverName, 0) > 1;
            })
            .count();
    }

    private String publicBlockReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "UNKNOWN";
        }
        if (reason.startsWith("NO_READY_NODE_")) {
            return reason.substring("NO_READY_NODE_".length());
        }
        if (reason.startsWith("ACTIVE_NODE_")) {
            return kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy.sanitize(reason.substring("ACTIVE_NODE_".length()));
        }
        return kr.lunaf.cloudislands.protocol.route.PlayerRouteMessagePolicy.sanitize(reason);
    }
}
