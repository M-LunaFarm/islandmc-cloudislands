package kr.lunaf.cloudislands.protocol.node;

import kr.lunaf.cloudislands.api.model.NodeState;

public record NodeHeartbeatRequest(
    String nodeId,
    String pool,
    String velocityServerName,
    NodeState state,
    int players,
    int activeIslands,
    double mspt,
    int activationQueue,
    long heapUsedMb,
    long heapMaxMb
) {}
