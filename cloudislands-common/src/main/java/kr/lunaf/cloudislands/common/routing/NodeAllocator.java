package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import kr.lunaf.cloudislands.api.model.NodeState;

public final class NodeAllocator {
    private final Duration heartbeatTimeout;

    public NodeAllocator(Duration heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now) {
        return selectBestNode(nodes, now, null);
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId) {
        return selectBestNode(nodes, now, templateId, "");
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion) {
        return selectBestNode(nodes, now, templateId, minNodeVersion, "island");
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId, String minNodeVersion, String pool) {
        List<NodeLoad> eligible = nodes.stream()
            .filter(node -> node.inPool(pool))
            .filter(node -> node.eligible(now, heartbeatTimeout))
            .filter(node -> node.supportsTemplate(templateId))
            .filter(node -> node.satisfiesMinVersion(minNodeVersion))
            .toList();
        Optional<NodeLoad> ready = eligible.stream()
            .filter(node -> node.state() == NodeState.READY)
            .min(Comparator.comparingDouble(NodeLoad::score));
        return ready.isPresent() ? ready : eligible.stream().min(Comparator.comparingDouble(NodeLoad::score));
    }

    public boolean acceptsExistingRoute(NodeLoad node, Instant now, String templateId, String minNodeVersion) {
        return acceptsExistingRoute(node, now, templateId, minNodeVersion, "island");
    }

    public boolean acceptsExistingRoute(NodeLoad node, Instant now, String templateId, String minNodeVersion, String pool) {
        return node.inPool(pool) && node.acceptsExistingRoute(now, heartbeatTimeout, templateId, minNodeVersion);
    }
}
