package kr.lunaf.cloudislands.common.routing;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class NodeAllocator {
    private final Duration heartbeatTimeout;

    public NodeAllocator(Duration heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now) {
        return selectBestNode(nodes, now, null);
    }

    public Optional<NodeLoad> selectBestNode(List<NodeLoad> nodes, Instant now, String templateId) {
        return nodes.stream()
            .filter(node -> node.eligible(now, heartbeatTimeout))
            .filter(node -> node.supportsTemplate(templateId))
            .min(Comparator.comparingDouble(NodeLoad::score));
    }
}
