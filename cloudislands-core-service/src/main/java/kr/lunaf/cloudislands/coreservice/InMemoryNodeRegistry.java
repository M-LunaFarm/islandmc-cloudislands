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
        Instant now = Instant.now();
        upsert(new NodeLoad("island-1", "Island-1", NodeState.SOFT_FULL, 92, 110, 480, 600, 42.0D, 8, 20, 0.40D, 6144, 8192, 0, now, true));
        upsert(new NodeLoad("island-2", "Island-2", NodeState.READY, 31, 110, 170, 600, 24.0D, 1, 20, 0.12D, 4096, 8192, 0, now, true));
    }

    @Override
    public void heartbeat(NodeHeartbeatRequest request) {
        NodeLoad current = nodes.get(request.nodeId());
        NodeState nextState = current != null && current.state() == NodeState.DRAINING ? NodeState.DRAINING : request.state();
        upsert(new NodeLoad(
            request.nodeId(),
            request.velocityServerName(),
            nextState,
            request.players(),
            current == null ? 110 : current.hardPlayerCap(),
            request.activeIslands(),
            current == null ? 600 : current.maxActiveIslands(),
            request.mspt(),
            request.activationQueue(),
            current == null ? 20 : current.maxActivationQueue(),
            current == null ? 0.0D : current.chunkLoadPressure(),
            request.heapUsedMb(),
            request.heapMaxMb(),
            current == null ? 0 : current.recentFailurePenalty(),
            Instant.now(),
            request.storageAvailable()
        ));
    }

    @Override
    public boolean drain(String nodeId) {
        return setState(nodeId, NodeState.DRAINING);
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
            upsert(new NodeLoad(node.nodeId(), node.velocityServerName(), NodeState.DOWN, node.players(), node.hardPlayerCap(), node.activeIslands(), node.maxActiveIslands(), node.mspt(), node.activationQueue(), node.maxActivationQueue(), node.chunkLoadPressure(), node.heapUsedMb(), node.heapMaxMb(), node.recentFailurePenalty(), node.lastHeartbeat(), node.storageAvailable()));
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
        upsert(new NodeLoad(node.nodeId(), node.velocityServerName(), state, node.players(), node.hardPlayerCap(), node.activeIslands(), node.maxActiveIslands(), node.mspt(), node.activationQueue(), node.maxActivationQueue(), node.chunkLoadPressure(), node.heapUsedMb(), node.heapMaxMb(), node.recentFailurePenalty(), node.lastHeartbeat(), node.storageAvailable()));
        return true;
    }

    private void upsert(NodeLoad node) {
        nodes.put(node.nodeId(), node);
    }
}
