package kr.lunaf.cloudislands.coreservice;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.common.routing.NodeLoad;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public interface NodeRegistry {
    void heartbeat(NodeHeartbeatRequest request);

    boolean drain(String nodeId);

    boolean undrain(String nodeId);

    List<String> markStaleDown(Duration heartbeatTimeout);

    List<NodeLoad> snapshot();

    Optional<NodeLoad> find(String nodeId);

    default String toJson() {
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

    static String toJson(NodeLoad node) {
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
            .append("\"storageAvailable\":").append(node.storageAvailable()).append(',')
            .append("\"lastHeartbeat\":\"").append(node.lastHeartbeat()).append("\",")
            .append("\"score\":").append(node.score())
            .append('}')
            .toString();
    }
}
