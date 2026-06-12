package kr.lunaf.cloudislands.protocol.node;

import kr.lunaf.cloudislands.api.model.NodeState;

public record NodeHeartbeatRequest(
    int protocolVersion,
    String nodeId,
    String pool,
    String velocityServerName,
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
    String supportedTemplates
) {
    public static final int CURRENT_PROTOCOL_VERSION = 1;
}
