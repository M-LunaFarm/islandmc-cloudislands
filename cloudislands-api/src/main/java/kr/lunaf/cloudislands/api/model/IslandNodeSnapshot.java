package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.Map;

public record IslandNodeSnapshot(
    String nodeId,
    String pool,
    String serverName,
    String nodeVersion,
    NodeState state,
    int players,
    int softPlayerCap,
    int hardPlayerCap,
    int reservedSlots,
    int activeIslands,
    int maxActiveIslands,
    double mspt,
    int activationQueue,
    int maxActivationQueue,
    double chunkLoadPressure,
    long heapUsedMb,
    long heapMaxMb,
    int recentFailurePenalty,
    boolean storageAvailable,
    String supportedTemplates,
    Instant lastHeartbeat,
    double score,
    Map<String, Double> scoreBreakdown
) {
    public IslandNodeSnapshot(
        String nodeId,
        String pool,
        String serverName,
        String nodeVersion,
        NodeState state,
        int players,
        int softPlayerCap,
        int hardPlayerCap,
        int reservedSlots,
        int activeIslands,
        int maxActiveIslands,
        double mspt,
        int activationQueue,
        int maxActivationQueue,
        double chunkLoadPressure,
        long heapUsedMb,
        long heapMaxMb,
        int recentFailurePenalty,
        boolean storageAvailable,
        String supportedTemplates,
        Instant lastHeartbeat,
        double score
    ) {
        this(nodeId, pool, serverName, nodeVersion, state, players, softPlayerCap, hardPlayerCap, reservedSlots, activeIslands, maxActiveIslands, mspt, activationQueue, maxActivationQueue, chunkLoadPressure, heapUsedMb, heapMaxMb, recentFailurePenalty, storageAvailable, supportedTemplates, lastHeartbeat, score, Map.of());
    }
}
