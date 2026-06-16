package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class InMemoryNodeRegistry implements NodeRegistry {
    private final Map<String, NodeLoad> nodes = new ConcurrentHashMap<>();

    public InMemoryNodeRegistry() {
        this(6);
    }

    public InMemoryNodeRegistry(int defaultNodeCount) {
        Instant now = Instant.now();
        int count = Math.max(0, defaultNodeCount);
        for (int index = 1; index <= count; index++) {
            boolean warmNode = index == 1;
            upsert(new NodeLoad(
                "island-" + index,
                "island",
                "Island-" + index,
                "",
                warmNode ? NodeState.SOFT_FULL : NodeState.READY,
                warmNode ? 92 : 20 + (index * 7),
                90,
                110,
                20,
                warmNode ? 480 : 120 + (index * 35),
                600,
                warmNode ? 42.0D : 18.0D + (index * 2.5D),
                warmNode ? 8 : index % 3,
                20,
                warmNode ? 0.40D : Math.min(0.08D * index, 0.60D),
                3072L + (index * 512L),
                8192,
                0,
                now,
                true,
                "*"
            ));
        }
    }

    @Override
    public void heartbeat(NodeHeartbeatRequest request) {
        NodeLoad current = nodes.get(request.nodeId());
        NodeState nextState = NodeRegistry.normalizeHeartbeatState(request, current == null ? request.state() : current.state());
        upsert(new NodeLoad(
            request.nodeId(),
            NodeRegistry.safeHeartbeatPool(request),
            NodeRegistry.safeHeartbeatVelocityServerName(request),
            request.nodeVersion(),
            nextState,
            request.players(),
            request.softPlayerCap(),
            request.hardPlayerCap(),
            request.reservedSlots(),
            request.activeIslands(),
            request.maxActiveIslands(),
            request.mspt(),
            request.activationQueue(),
            request.maxActivationQueue(),
            request.chunkLoadPressure(),
            request.heapUsedMb(),
            request.heapMaxMb(),
            request.recentFailurePenalty(),
            Instant.now(),
            request.storageAvailable(),
            request.supportedTemplates()
        ));
    }

    @Override
    public boolean drain(String nodeId) {
        return setState(nodeId, NodeState.DRAINING);
    }

    @Override
    public boolean shutdownSafe(String nodeId) {
        return setState(nodeId, NodeState.SHUTTING_DOWN);
    }

    @Override
    public boolean undrain(String nodeId) {
        return setState(nodeId, NodeState.READY);
    }

    @Override
    public List<String> markStaleDown(Duration heartbeatTimeout) {
        Instant now = Instant.now();
        List<String> down = new ArrayList<>();
        for (NodeLoad node : nodes.values()) {
            if (node.state() == NodeState.DOWN || (node.lastHeartbeat() != null && !node.lastHeartbeat().plus(heartbeatTimeout).isBefore(now))) {
                continue;
            }
            upsert(new NodeLoad(node.nodeId(), node.pool(), node.velocityServerName(), node.nodeVersion(), NodeState.DOWN, node.players(), node.softPlayerCap(), node.hardPlayerCap(), node.reservedSlots(), node.activeIslands(), node.maxActiveIslands(), node.mspt(), node.activationQueue(), node.maxActivationQueue(), node.chunkLoadPressure(), node.heapUsedMb(), node.heapMaxMb(), node.recentFailurePenalty(), node.lastHeartbeat(), node.storageAvailable(), node.supportedTemplates()));
            down.add(node.nodeId());
        }
        return List.copyOf(down);
    }

    @Override
    public List<NodeLoad> snapshot() {
        return nodes.values().stream().sorted(Comparator.comparing(NodeLoad::nodeId)).toList();
    }

    @Override
    public Optional<NodeLoad> find(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    private boolean setState(String nodeId, NodeState state) {
        NodeLoad node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        upsert(new NodeLoad(node.nodeId(), node.pool(), node.velocityServerName(), node.nodeVersion(), state, node.players(), node.softPlayerCap(), node.hardPlayerCap(), node.reservedSlots(), node.activeIslands(), node.maxActiveIslands(), node.mspt(), node.activationQueue(), node.maxActivationQueue(), node.chunkLoadPressure(), node.heapUsedMb(), node.heapMaxMb(), node.recentFailurePenalty(), node.lastHeartbeat(), node.storageAvailable(), node.supportedTemplates()));
        return true;
    }

    private void upsert(NodeLoad node) {
        nodes.put(node.nodeId(), node);
    }
}
