package kr.lunaf.cloudislands.common.routing;

import kr.lunaf.cloudislands.api.model.NodeState;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeAllocatorTest {
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");

    @Test
    void selectsLeastLoadedReadyNodeFromSixIslandNodes() {
        NodeAllocator allocator = new NodeAllocator(Duration.ofSeconds(5));
        List<NodeLoad> nodes = List.of(
                node("node-a", "server-a", NodeState.READY, 80, 440, 35.0, 5),
                node("node-b", "server-b", NodeState.READY, 55, 280, 29.0, 3),
                node("node-c", "server-c", NodeState.SOFT_FULL, 90, 400, 30.0, 2),
                node("node-d", "server-d", NodeState.DRAINING, 5, 10, 18.0, 0),
                node("node-e", "server-e", NodeState.READY, 12, 90, 20.0, 0),
                node("node-f", "server-f", NodeState.HARD_FULL, 110, 600, 45.0, 20)
        );

        NodeLoad selected = allocator.selectReadyNode(nodes, NOW, "default", "1.0.0", "island").orElseThrow();

        assertEquals("node-e", selected.nodeId());
        assertEquals("server-e", selected.velocityServerName());
        assertEquals(3L, allocator.readyNodeCandidateCount(nodes, NOW, "default", "1.0.0", "island"));
    }

    @Test
    void ignoresDuplicateVelocityServerNamesInsidePool() {
        NodeAllocator allocator = new NodeAllocator(Duration.ofSeconds(5));
        List<NodeLoad> nodes = List.of(
                node("node-a", "server-shared", NodeState.READY, 5, 20, 18.0, 0),
                node("node-b", "server-shared", NodeState.READY, 1, 5, 18.0, 0),
                node("node-c", "server-c", NodeState.READY, 40, 180, 24.0, 2),
                node("node-d", "server-d", NodeState.SOFT_FULL, 85, 320, 30.0, 4),
                node("node-e", "server-e", NodeState.HARD_FULL, 110, 600, 45.0, 20),
                node("node-f", "server-f", NodeState.DRAINING, 0, 0, 18.0, 0)
        );

        NodeLoad selected = allocator.selectReadyNode(nodes, NOW, "default", "1.0.0", "island").orElseThrow();

        assertEquals("node-c", selected.nodeId());
        assertEquals(1L, allocator.readyNodeCandidateCount(nodes, NOW, "default", "1.0.0", "island"));
        assertEquals("DUPLICATE_VELOCITY_SERVER_NAME",
                allocator.targetNodeBlockReason(nodes, NOW, "node-a", "default", "1.0.0", "island"));
        assertEquals("DUPLICATE_VELOCITY_SERVER_NAME",
                allocator.existingRouteBlockReason(nodes, nodes.get(1), NOW, "default", "1.0.0", "island"));
    }

    @Test
    void reportsSoftFullWhenAllReadyNodesAreUnavailableForNewActivation() {
        NodeAllocator allocator = new NodeAllocator(Duration.ofSeconds(5));
        List<NodeLoad> nodes = List.of(
                node("node-a", "server-a", NodeState.SOFT_FULL, 90, 300, 30.0, 1),
                node("node-b", "server-b", NodeState.SOFT_FULL, 92, 310, 31.0, 1),
                node("node-c", "server-c", NodeState.DRAINING, 0, 0, 18.0, 0),
                node("node-d", "server-d", NodeState.DOWN, 0, 0, 18.0, 0),
                node("node-e", "server-e", NodeState.DOWN, 0, 0, 18.0, 0),
                node("node-f", "server-f", NodeState.WARMING, 0, 0, 18.0, 0)
        );

        assertTrue(allocator.selectReadyNode(nodes, NOW, "default", "1.0.0", "island").isEmpty());
        assertEquals(0L, allocator.readyNodeCandidateCount(nodes, NOW, "default", "1.0.0", "island"));
        assertEquals("STATE_SOFT_FULL", allocator.readyNodeBlockReason(nodes, NOW, "default", "1.0.0", "island"));
    }

    private NodeLoad node(String nodeId, String velocityServerName, NodeState state, int players, int activeIslands, double mspt, int activationQueue) {
        return new NodeLoad(
                nodeId,
                "island",
                velocityServerName,
                "1.2.0",
                state,
                players,
                90,
                110,
                15,
                activeIslands,
                600,
                mspt,
                activationQueue,
                20,
                0.10,
                2048,
                8192,
                0,
                NOW.minusSeconds(1),
                true,
                "*"
        );
    }
}
