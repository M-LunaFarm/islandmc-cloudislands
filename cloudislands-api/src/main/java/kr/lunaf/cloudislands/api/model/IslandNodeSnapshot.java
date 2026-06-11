package kr.lunaf.cloudislands.api.model;

import java.time.Instant;

public record IslandNodeSnapshot(
    String nodeId,
    String serverName,
    String nodeVersion,
    NodeState state,
    int players,
    int softPlayerCap,
    int hardPlayerCap,
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
) {}
