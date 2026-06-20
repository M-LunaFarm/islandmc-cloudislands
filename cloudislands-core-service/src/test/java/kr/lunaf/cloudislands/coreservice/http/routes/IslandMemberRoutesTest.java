package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import org.junit.jupiter.api.Test;

class IslandMemberRoutesTest {
    @Test
    void registersIslandMemberEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandMemberRoutes routes = new IslandMemberRoutes(null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/islands/members"));
        assertTrue(paths.contains("/v1/players/islands"));
        assertTrue(paths.contains("/v1/islands/members/set"));
        assertTrue(paths.contains("/v1/islands/members/trust-temporary"));
        assertTrue(paths.contains("/v1/islands/transfer"));
        assertTrue(paths.contains("/v1/islands/members/remove"));
    }

    @Test
    void rendersMemberContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        IslandMemberSnapshot member = new IslandMemberSnapshot(islandId, playerUuid, IslandRole.CO_OWNER, Instant.parse("2026-01-02T03:04:05Z"));
        IslandMemberSnapshot temporary = new IslandMemberSnapshot(islandId, UUID.fromString("00000000-0000-0000-0000-000000000004"), IslandRole.TRUSTED, Instant.parse("2026-01-02T04:04:05Z"), Instant.parse("2026-01-02T05:04:05Z"));

        assertEquals(IslandRole.CO_OWNER, IslandMemberRoutes.memberRole(List.of(member), playerUuid));
        assertNull(IslandMemberRoutes.memberRole(List.of(member), UUID.fromString("00000000-0000-0000-0000-000000000003")));
        assertEquals(
            "{\"members\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"playerUuid\":\"00000000-0000-0000-0000-000000000002\",\"role\":\"CO_OWNER\",\"joinedAt\":\"2026-01-02T03:04:05Z\",\"expiresAt\":null},{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"playerUuid\":\"00000000-0000-0000-0000-000000000004\",\"role\":\"TRUSTED\",\"joinedAt\":\"2026-01-02T04:04:05Z\",\"expiresAt\":\"2026-01-02T05:04:05Z\"}]}",
            IslandMemberRoutes.membersJson(List.of(member, temporary))
        );

        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        profiles.touch(playerUuid, "LunaFarm", "ko_kr");
        String enriched = IslandMemberRoutes.membersJson(List.of(member), profiles);
        assertTrue(enriched.contains("\"playerName\":\"LunaFarm\""));
        assertTrue(enriched.contains("\"presenceState\":\"RECENT_ACTIVITY\""));
        assertTrue(enriched.contains("\"presenceSource\":\"CORE_PLAYER_PROFILE\""));
        assertTrue(enriched.contains("\"lastSeenAt\":\""));
    }

    @Test
    void rendersPlayerIslandsContract() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandSnapshot island = new IslandSnapshot(
            islandId,
            ownerUuid,
            "Sky \"Base\"",
            IslandState.ACTIVE,
            100,
            7L,
            "12.5",
            true,
            Instant.parse("2026-01-02T03:04:05Z"),
            Instant.parse("2026-01-03T03:04:05Z")
        );

        assertEquals(
            "{\"islands\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"ownerUuid\":\"00000000-0000-0000-0000-000000000002\",\"name\":\"Sky \\\"Base\\\"\",\"state\":\"ACTIVE\",\"size\":100,\"border\":100,\"level\":7,\"worth\":\"12.5\",\"publicAccess\":true,\"createdAt\":\"2026-01-02T03:04:05Z\",\"updatedAt\":\"2026-01-03T03:04:05Z\"}]}",
            IslandMemberRoutes.islandsJson(List.of(island))
        );
    }
}
