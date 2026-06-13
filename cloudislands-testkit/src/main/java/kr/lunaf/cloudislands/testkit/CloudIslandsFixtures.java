package kr.lunaf.cloudislands.testkit;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

public final class CloudIslandsFixtures {
    public static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    private CloudIslandsFixtures() {}

    public static UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("cloudislands-testkit:" + key).getBytes(StandardCharsets.UTF_8));
    }

    public static IslandSnapshot island() {
        return island(uuid("island"), uuid("owner"), "Fixture Island", IslandState.INACTIVE_READY);
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
}
