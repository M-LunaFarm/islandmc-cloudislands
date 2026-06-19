package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
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

        assertEquals(
            "{\"accepted\":true,\"inviteId\":\"00000000-0000-0000-0000-000000000001\",\"islandId\":\"00000000-0000-0000-0000-000000000002\",\"inviterUuid\":\"00000000-0000-0000-0000-000000000003\",\"targetUuid\":\"00000000-0000-0000-0000-000000000004\",\"state\":\"PENDING\",\"createdAt\":\"2026-01-02T03:04:05Z\",\"expiresAt\":\"2026-01-09T03:04:05Z\"}",
            IslandVisitorRoutes.inviteAcceptedJson(invite)
        );
        assertEquals(
            "{\"invites\":[{\"inviteId\":\"00000000-0000-0000-0000-000000000001\",\"islandId\":\"00000000-0000-0000-0000-000000000002\",\"inviterUuid\":\"00000000-0000-0000-0000-000000000003\",\"targetUuid\":\"00000000-0000-0000-0000-000000000004\",\"state\":\"PENDING\",\"createdAt\":\"2026-01-02T03:04:05Z\",\"expiresAt\":\"2026-01-09T03:04:05Z\"}]}",
            IslandVisitorRoutes.invitesJson(List.of(invite))
        );
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
        assertEquals(
            "{\"bans\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"bannedUuid\":\"00000000-0000-0000-0000-000000000002\",\"actorUuid\":\"00000000-0000-0000-0000-000000000003\",\"reason\":\"bad \\\"visit\\\"\",\"createdAt\":\"2026-01-02T03:04:05Z\",\"expiresAt\":null}]}",
            IslandVisitorRoutes.bansJson(List.of(ban))
        );
    }
}
