package kr.lunaf.cloudislands.api.model;

import java.time.Instant;

public record IslandNodeSnapshot(
    String nodeId,
    String serverName,
    String nodeVersion,
    NodeState state,
    int players,
    int hardPlayerCap,
    int activeIslands,
    int maxActiveIslands,
    double mspt,
    int activationQueue,
    int maxActivationQueue,
    long heapUsedMb,
    long heapMaxMb,
    boolean storageAvailable,
    String supportedTemplates,
    Instant lastHeartbeat,
    double score
) {}
