package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class InMemoryNodeRegistry {
    private final Map<String, NodeLoad> nodes = new ConcurrentHashMap<>();

    public InMemoryNodeRegistry() {
        Instant now = Instant.now();
        upsert(new NodeLoad("island-1", "Island-1", NodeState.SOFT_FULL, 92, 110, 480, 600, 42.0D, 8, 20, 0.40D, 6144, 8192, 0, now));
        upsert(new NodeLoad("island-2", "Island-2", NodeState.READY, 31, 110, 170, 600, 24.0D, 1, 20, 0.12D, 4096, 8192, 0, now));
    }

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
            Instant.now()
        ));
    }

    public boolean drain(String nodeId) {
        return setState(nodeId, NodeState.DRAINING);
    }

    public boolean undrain(String nodeId) {
        return setState(nodeId, NodeState.READY);
    }

    public List<NodeLoad> snapshot() {
        return nodes.values().stream().sorted(Comparator.comparing(NodeLoad::nodeId)).toList();
    }

    public Optional<NodeLoad> find(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"nodes\":[");
        boolean first = true;
        for (NodeLoad node : snapshot()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(toJson(node));
        }
        return builder.append("]}").toString();
    }

    public static String toJson(NodeLoad node) {
        return new StringBuilder("{")
            .append("\"id\":\"").append(node.nodeId()).append("\",")
            .append("\"server\":\"").append(node.velocityServerName()).append("\",")
            .append("\"state\":\"").append(node.state()).append("\",")
            .append("\"players\":").append(node.players()).append(',')
            .append("\"hardPlayerCap\":").append(node.hardPlayerCap()).append(',')
            .append("\"activeIslands\":").append(node.activeIslands()).append(',')
            .append("\"maxActiveIslands\":").append(node.maxActiveIslands()).append(',')
            .append("\"mspt\":").append(node.mspt()).append(',')
            .append("\"activationQueue\":").append(node.activationQueue()).append(',')
            .append("\"maxActivationQueue\":").append(node.maxActivationQueue()).append(',')
            .append("\"heapUsedMb\":").append(node.heapUsedMb()).append(',')
            .append("\"heapMaxMb\":").append(node.heapMaxMb()).append(',')
            .append("\"lastHeartbeat\":\"").append(node.lastHeartbeat()).append("\",")
            .append("\"score\":").append(node.score())
            .append('}')
            .toString();
    }

    private boolean setState(String nodeId, NodeState state) {
        NodeLoad node = nodes.get(nodeId);
        if (node == null) {
            return false;
        }
        upsert(new NodeLoad(node.nodeId(), node.velocityServerName(), state, node.players(), node.hardPlayerCap(), node.activeIslands(), node.maxActiveIslands(), node.mspt(), node.activationQueue(), node.maxActivationQueue(), node.chunkLoadPressure(), node.heapUsedMb(), node.heapMaxMb(), node.recentFailurePenalty(), node.lastHeartbeat()));
        return true;
    }

    private void upsert(NodeLoad node) {
        nodes.put(node.nodeId(), node);
    }
}
