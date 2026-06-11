package kr.lunaf.cloudislands.coreservice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.common.routing.NodeLoad;

public final class InMemoryNodeRegistry {
    private final List<NodeLoad> nodes = new ArrayList<>();

    public InMemoryNodeRegistry() {
        Instant now = Instant.now();
        nodes.add(new NodeLoad("island-1", "Island-1", NodeState.SOFT_FULL, 92, 110, 480, 600, 42.0D, 8, 20, 0.40D, 6144, 8192, 0, now));
        nodes.add(new NodeLoad("island-2", "Island-2", NodeState.READY, 31, 110, 170, 600, 24.0D, 1, 20, 0.12D, 4096, 8192, 0, now));
    }

    public List<NodeLoad> snapshot() {
        return List.copyOf(nodes);
    }

    public String toJson() {
        return "{\"nodes\": " + nodes.size() + "}";
    }
}
