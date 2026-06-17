package kr.lunaf.cloudislands.testkit;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class CloudIslandsFixtures {
    public static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    public static final UUID PLAYER_UUID = uuid("player");
    public static final UUID ISLAND_UUID = uuid("island");
    public static final UUID TICKET_UUID = uuid("ticket:" + PLAYER_UUID + ":" + ISLAND_UUID);

    private CloudIslandsFixtures() {}

    public static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("cloudislands-testkit:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static IslandSnapshot island() {
        return island(ISLAND_UUID, uuid("owner"), "Fixture Island", IslandState.INACTIVE_READY);
    }

    public static IslandSnapshot island(UUID islandId, UUID ownerUuid, String name, IslandState state) {
        return new IslandSnapshot(
            islandId,
            ownerUuid,
            name,
            state,
            300,
            1L,
            "0.00",
            true,
            FIXED_TIME,
            FIXED_TIME
        );
    }

    public static IslandRuntimeSnapshot activeRuntime(UUID islandId, String nodeId) {
        return new IslandRuntimeSnapshot(
            islandId,
            IslandState.ACTIVE,
            nodeId,
            "ci_shard_001",
            0,
            0,
            nodeId + "-lease",
            1L,
            FIXED_TIME,
            FIXED_TIME
        );
    }

    public static RouteTicket readyHomeTicket() {
        return readyHomeTicket(PLAYER_UUID, ISLAND_UUID, "island-1");
    }

    public static RouteTicket readyHomeTicket(UUID playerUuid, UUID islandId, String nodeId) {
        return new RouteTicket(
            uuid("ticket:" + playerUuid + ":" + islandId),
            playerUuid,
            RouteAction.HOME,
            islandId,
            nodeId,
            "ci_shard_001",
            RouteTicketState.READY,
            FIXED_TIME.plusSeconds(30L),
            "fixture-nonce",
            Map.of("home", "default")
        );
    }

    public static IslandNodeSnapshot node(String nodeId) {
        return node(nodeId, NodeState.READY, 0, 0, 0);
    }

    public static List<IslandNodeSnapshot> islandNodePool(int count) {
        return islandNodePool(count, -1);
    }

    public static List<IslandNodeSnapshot> islandNodePool(int count, int softFullNode) {
        int safeCount = Math.max(2, Math.min(6, count));
        List<IslandNodeSnapshot> nodes = new ArrayList<>(safeCount);
        for (int index = 1; index <= safeCount; index++) {
            String nodeId = "island-" + index;
            if (index == softFullNode) {
                nodes.add(node(nodeId, NodeState.SOFT_FULL, 95, 480, 8));
            } else {
                nodes.add(node(nodeId, NodeState.READY, 20 + index, 100 + (index * 10), index - 1));
            }
        }
        return List.copyOf(nodes);
    }

    public static IslandNodeSnapshot node(String nodeId, NodeState state, int players, int activeIslands, int activationQueue) {
        return new IslandNodeSnapshot(
            nodeId,
            "island",
            nodeId,
            "testkit",
            state,
            players,
            90,
            110,
            15,
            activeIslands,
            600,
            20.0D,
            activationQueue,
            20,
            0.0D,
            256L,
            1024L,
            0,
            true,
            "default",
            FIXED_TIME,
            0.0D,
            Map.of()
        );
    }

    public static GlobalEventSnapshot event(long sequence, String type, UUID islandId) {
        return new GlobalEventSnapshot(sequence, type, Map.of("islandId", islandId.toString()), FIXED_TIME);
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
