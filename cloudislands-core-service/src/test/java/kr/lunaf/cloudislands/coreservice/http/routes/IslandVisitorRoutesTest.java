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
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class IslandVisitorRoutesTest {
    @Test
    void registersIslandVisitorEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandVisitorRoutes routes = new IslandVisitorRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(8, paths.size());
        assertTrue(paths.contains("/v1/islands/invites"));
        assertTrue(paths.contains("/v1/players/invites"));
        assertTrue(paths.contains("/v1/islands/invites/accept"));
        assertTrue(paths.contains("/v1/islands/invites/decline"));
        assertTrue(paths.contains("/v1/islands/bans/set"));
        assertTrue(paths.contains("/v1/islands/bans"));
        assertTrue(paths.contains("/v1/islands/bans/remove"));
        assertTrue(paths.contains("/v1/islands/visitors/kick"));
    }

    @Test
    void rendersInviteContracts() {
        UUID inviteId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID inviterUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID targetUuid = UUID.fromString("00000000-0000-0000-0000-000000000004");
        IslandInviteSnapshot invite = new IslandInviteSnapshot(
            inviteId,
            islandId,
            inviterUuid,
            targetUuid,
            "PENDING",
            Instant.parse("2026-01-02T03:04:05Z"),
            Instant.parse("2026-01-09T03:04:05Z")
        );

        Map<?, ?> accepted = SimpleJson.object(SimpleJson.parse(IslandVisitorRoutes.inviteAcceptedJson(invite)));
        Map<?, ?> invites = SimpleJson.object(SimpleJson.parse(IslandVisitorRoutes.invitesJson(List.of(invite))));
        Map<?, ?> listedInvite = SimpleJson.object(SimpleJson.list(invites.get("invites")).get(0));

        assertEquals(true, accepted.get("accepted"));
        assertInvite(inviteId, islandId, inviterUuid, targetUuid, accepted);
        assertInvite(inviteId, islandId, inviterUuid, targetUuid, listedInvite);
    }

    @Test
    void rendersBanContractsAndFindsRoles() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID bannedUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000003");
        IslandBanSnapshot ban = new IslandBanSnapshot(
            islandId,
            bannedUuid,
            actorUuid,
            "bad \"visit\"",
            Instant.parse("2026-01-02T03:04:05Z"),
            null
        );
        IslandMemberSnapshot member = new IslandMemberSnapshot(islandId, bannedUuid, IslandRole.BANNED, Instant.parse("2026-01-02T03:04:05Z"));

        assertEquals(IslandRole.BANNED, IslandVisitorRoutes.memberRole(List.of(member), bannedUuid));
        assertNull(IslandVisitorRoutes.memberRole(List.of(member), actorUuid));
        Map<?, ?> bans = SimpleJson.object(SimpleJson.parse(IslandVisitorRoutes.bansJson(List.of(ban))));
        Map<?, ?> listedBan = SimpleJson.object(SimpleJson.list(bans.get("bans")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(listedBan.get("islandId")));
        assertEquals(bannedUuid.toString(), SimpleJson.text(listedBan.get("bannedUuid")));
        assertEquals(actorUuid.toString(), SimpleJson.text(listedBan.get("actorUuid")));
        assertEquals("bad \"visit\"", SimpleJson.text(listedBan.get("reason")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(listedBan.get("createdAt")));
        assertEquals(null, listedBan.get("expiresAt"));
    }

    private static void assertInvite(UUID inviteId, UUID islandId, UUID inviterUuid, UUID targetUuid, Map<?, ?> invite) {
        assertEquals(inviteId.toString(), SimpleJson.text(invite.get("inviteId")));
        assertEquals(islandId.toString(), SimpleJson.text(invite.get("islandId")));
        assertEquals(inviterUuid.toString(), SimpleJson.text(invite.get("inviterUuid")));
        assertEquals(targetUuid.toString(), SimpleJson.text(invite.get("targetUuid")));
        assertEquals("PENDING", SimpleJson.text(invite.get("state")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(invite.get("createdAt")));
        assertEquals("2026-01-09T03:04:05Z", SimpleJson.text(invite.get("expiresAt")));
    }
}
