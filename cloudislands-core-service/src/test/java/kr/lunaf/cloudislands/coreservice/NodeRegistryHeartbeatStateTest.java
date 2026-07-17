package kr.lunaf.cloudislands.coreservice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import org.junit.jupiter.api.Test;

class NodeRegistryHeartbeatStateTest {
    @Test
    void startingHeartbeatRejoinsAfterGracefulProcessShutdown() {
        assertEquals(
            NodeState.STARTING,
            NodeRegistry.normalizeHeartbeatState(heartbeat(NodeState.STARTING), NodeState.SHUTTING_DOWN)
        );
        assertEquals(
            NodeState.SHUTTING_DOWN,
            NodeRegistry.normalizeHeartbeatState(heartbeat(NodeState.READY), NodeState.SHUTTING_DOWN),
            "ordinary periodic heartbeats must not cancel an in-progress shutdown"
        );
    }

    @Test
    void operatorDrainSurvivesProcessRestart() {
        assertEquals(
            NodeState.DRAINING,
            NodeRegistry.normalizeHeartbeatState(heartbeat(NodeState.STARTING), NodeState.DRAINING)
        );
    }

    private NodeHeartbeatRequest heartbeat(NodeState state) {
        return new NodeHeartbeatRequest(
            NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
            "single-paper-01",
            "single-paper",
            "single-paper-01",
            "test",
            state,
            0,
            80,
            100,
            15,
            0,
            100,
            20.0D,
            0,
            10,
            0.0D,
            128L,
            512L,
            0,
            true,
            "*"
        );
    }
}
