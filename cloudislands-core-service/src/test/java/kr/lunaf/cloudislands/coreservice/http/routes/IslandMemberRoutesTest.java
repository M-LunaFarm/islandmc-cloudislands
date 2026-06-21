package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
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
        Map<?, ?> members = SimpleJson.object(SimpleJson.parse(IslandMemberRoutes.membersJson(List.of(member, temporary))));
        Map<?, ?> renderedMember = SimpleJson.object(SimpleJson.list(members.get("members")).get(0));
        Map<?, ?> renderedTemporary = SimpleJson.object(SimpleJson.list(members.get("members")).get(1));

        assertMember(islandId, playerUuid, "CO_OWNER", renderedMember);
        assertEquals(null, renderedMember.get("expiresAt"));
        assertMember(islandId, temporary.playerUuid(), "TRUSTED", renderedTemporary);
        assertEquals("2026-01-02T05:04:05Z", SimpleJson.text(renderedTemporary.get("expiresAt")));

        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        profiles.touch(playerUuid, "LunaFarm", "ko_kr");
        Map<?, ?> enrichedRoot = SimpleJson.object(SimpleJson.parse(IslandMemberRoutes.membersJson(List.of(member), profiles)));
        Map<?, ?> enriched = SimpleJson.object(SimpleJson.list(enrichedRoot.get("members")).get(0));
        assertEquals("LunaFarm", SimpleJson.text(enriched.get("playerName")));
        assertEquals("RECENT_ACTIVITY", SimpleJson.text(enriched.get("presenceState")));
        assertEquals("CORE_PLAYER_PROFILE", SimpleJson.text(enriched.get("presenceSource")));
        assertTrue(!SimpleJson.text(enriched.get("lastSeenAt")).isBlank());

        Map<?, ?> trusted = SimpleJson.object(SimpleJson.parse(
            IslandMemberRoutes.temporaryTrustJson(islandId, playerUuid, Instant.parse("2026-01-02T05:04:05Z"), 3600L)
        ));
        assertEquals(true, trusted.get("accepted"));
        assertEquals("TRUSTED", SimpleJson.text(trusted.get("roleKey")));
        assertEquals(3600L, ((Number) trusted.get("durationSeconds")).longValue());
    }

    @Test
    void rendersDynamicMemberRoleKeys() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000022");
        IslandMemberSnapshot member = new IslandMemberSnapshot(islandId, playerUuid, "builder", Instant.parse("2026-01-02T03:04:05Z"), null);

        assertNull(IslandMemberRoutes.memberRole(List.of(member), playerUuid));
        assertEquals("BUILDER", member.effectiveRoleKey());
        Map<?, ?> members = SimpleJson.object(SimpleJson.parse(IslandMemberRoutes.membersJson(List.of(member))));
        assertMember(islandId, playerUuid, "BUILDER", SimpleJson.object(SimpleJson.list(members.get("members")).get(0)));
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

        Map<?, ?> islands = SimpleJson.object(SimpleJson.parse(IslandMemberRoutes.islandsJson(List.of(island))));
        Map<?, ?> renderedIsland = SimpleJson.object(SimpleJson.list(islands.get("islands")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(renderedIsland.get("islandId")));
        assertEquals(ownerUuid.toString(), SimpleJson.text(renderedIsland.get("ownerUuid")));
        assertEquals("Sky \"Base\"", SimpleJson.text(renderedIsland.get("name")));
        assertEquals("ACTIVE", SimpleJson.text(renderedIsland.get("state")));
        assertEquals(100, ((Number) renderedIsland.get("size")).intValue());
        assertEquals(7L, ((Number) renderedIsland.get("level")).longValue());
        assertEquals("12.5", SimpleJson.text(renderedIsland.get("worth")));
        assertEquals(true, renderedIsland.get("publicAccess"));
    }

    private static void assertMember(UUID islandId, UUID playerUuid, String roleKey, Map<?, ?> member) {
        assertEquals(islandId.toString(), SimpleJson.text(member.get("islandId")));
        assertEquals(playerUuid.toString(), SimpleJson.text(member.get("playerUuid")));
        assertEquals(roleKey, SimpleJson.text(member.get("role")));
        assertEquals(roleKey, SimpleJson.text(member.get("roleKey")));
        assertTrue(!SimpleJson.text(member.get("joinedAt")).isBlank());
    }
}
