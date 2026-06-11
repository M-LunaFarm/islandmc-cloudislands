package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        int hardCap = current == null ? 110 : current.hardPlayerCap();
        int maxActiveIslands = current == null ? 600 : current.maxActiveIslands();
        int maxQueue = current == null ? 20 : current.maxActivationQueue();
        double chunkPressure = current == null ? 0.0D : current.chunkLoadPressure();
        int failurePenalty = current == null ? 0 : current.recentFailurePenalty();
        upsert(new NodeLoad(
            request.nodeId(),
            request.velocityServerName(),
            request.state(),
            request.players(),
            hardCap,
            request.activeIslands(),
            maxActiveIslands,
            request.mspt(),
            request.activationQueue(),
            maxQueue,
            chunkPressure,
            request.heapUsedMb(),
            request.heapMaxMb(),
            failurePenalty,
            Instant.now()
        ));
    }

    public void markDraining(String nodeId) {
        NodeLoad node = nodes.get(nodeId);
        if (node != null) {
            upsert(new NodeLoad(node.nodeId(), node.velocityServerName(), NodeState.DRAINING, node.players(), node.hardPlayerCap(), node.activeIslands(), node.maxActiveIslands(), node.mspt(), node.activationQueue(), node.maxActivationQueue(), node.chunkLoadPressure(), node.heapUsedMb(), node.heapMaxMb(), node.recentFailurePenalty(), node.lastHeartbeat()));
        }
    }

    public List<NodeLoad> snapshot() {
        return nodes.values().stream().sorted(Comparator.comparing(NodeLoad::nodeId)).toList();
    }

    public String toJson() {
        StringBuilder builder = new StringBuilder("{\"nodes\":[");
        boolean first = true;
        for (NodeLoad node : snapshot()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"id\":\"").append(node.nodeId()).append("\",")
                .append("\"server\":\"").append(node.velocityServerName()).append("\",")
                .append("\"state\":\"").append(node.state()).append("\",")
                .append("\"players\":").append(node.players()).append(',')
                .append("\"activeIslands\":").append(node.activeIslands()).append(',')
                .append("\"mspt\":").append(node.mspt()).append(',')
                .append("\"activationQueue\":").append(node.activationQueue()).append(',')
                .append("\"score\":").append(node.score())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private void upsert(NodeLoad node) {
        nodes.put(node.nodeId(), node);
    }
}
