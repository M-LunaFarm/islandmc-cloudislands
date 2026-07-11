package kr.lunaf.cloudislands.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import org.junit.jupiter.api.Test;

class CloudEventMapperCoopTest {
    @Test
    void mapsDedicatedCoopLifecycleEvents() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000141");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000142");
        Instant occurredAt = Instant.parse("2026-07-11T00:00:00Z");

        IslandCoopAddEvent added = assertInstanceOf(IslandCoopAddEvent.class, map("ISLAND_COOP_ADDED", islandId, playerUuid, occurredAt));
        IslandCoopRemoveEvent removed = assertInstanceOf(IslandCoopRemoveEvent.class, map("ISLAND_COOP_REMOVED", islandId, playerUuid, occurredAt));

        assertEquals(islandId, added.islandId());
        assertEquals(playerUuid, added.playerUuid());
        assertEquals(playerUuid, removed.playerUuid());
        assertEquals(occurredAt, removed.occurredAt());
    }

    private static CloudEvent map(String type, UUID islandId, UUID playerUuid, Instant occurredAt) {
        return CloudEventMapper.map(new GlobalEventSnapshot(1L, type, Map.of(
            "islandId", islandId.toString(),
            "playerUuid", playerUuid.toString()
        ), occurredAt)).orElseThrow();
    }
}
