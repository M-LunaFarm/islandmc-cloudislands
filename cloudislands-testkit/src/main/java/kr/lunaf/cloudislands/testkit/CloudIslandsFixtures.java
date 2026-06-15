package kr.lunaf.cloudislands.testkit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class CloudIslandsFixtures {
    public static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    public static final UUID ISLAND_UUID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    public static final UUID TICKET_UUID = UUID.fromString("00000000-0000-0000-0000-000000000301");

    private CloudIslandsFixtures() {
    }

    public static RouteTicket readyHomeTicket() {
        return new RouteTicket(
            TICKET_UUID,
            PLAYER_UUID,
            RouteAction.HOME,
            ISLAND_UUID,
            "island-1",
            "ci_shard_001",
            RouteTicketState.READY,
            Instant.parse("2026-01-01T00:00:30Z"),
            "fixture-nonce",
            Map.of("homeName", "default")
        );
    }

    public static NodeHeartbeatRequest readyIslandNode() {
        return new NodeHeartbeatRequest(
            NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
            "island-1",
            "island",
            "Island-1",
            "testkit",
            NodeState.READY,
            10,
            90,
            110,
            15,
            25,
            600,
            20.0D,
            0,
            20,
            0.0D,
            512L,
            2048L,
            0,
            true,
            "*"
        );
    }

    public static NodeHeartbeatRequest softFullIslandNode() {
        NodeHeartbeatRequest ready = readyIslandNode();
        return new NodeHeartbeatRequest(
            ready.protocolVersion(),
            ready.nodeId(),
            ready.pool(),
            ready.velocityServerName(),
            ready.nodeVersion(),
            NodeState.SOFT_FULL,
            95,
            ready.softPlayerCap(),
            ready.hardPlayerCap(),
            ready.reservedSlots(),
            ready.activeIslands(),
            ready.maxActiveIslands(),
            42.0D,
            8,
            ready.maxActivationQueue(),
            0.4D,
            ready.heapUsedMb(),
            ready.heapMaxMb(),
            1,
            ready.storageAvailable(),
            ready.supportedTemplates()
        );
    }
}
