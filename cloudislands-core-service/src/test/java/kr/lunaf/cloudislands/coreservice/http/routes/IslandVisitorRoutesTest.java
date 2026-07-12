package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import org.junit.jupiter.api.Test;

class IslandVisitorRoutesTest {
    @Test
    void visitorBanRechecksMembershipInsideTheLockedTransaction() throws Exception {
        String jdbc = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutes.java"));
        int operation = jdbc.indexOf("public String banVisitorResult(");
        int commit = jdbc.indexOf("connection.commit();", operation);

        assertTrue(operation >= 0 && commit > operation);
        String transaction = jdbc.substring(operation, commit);
        assertTrue(transaction.contains("SELECT owner_uuid FROM islands"));
        assertTrue(transaction.contains("FOR UPDATE"));
        assertTrue(transaction.contains("currentMemberRole(connection, islandId, playerUuid)"));
        assertTrue(transaction.contains("CoreRoleKeys.memberRole(currentRole)"));
        assertTrue(transaction.contains("banVisitorSql(connection)"));
        assertTrue(routes.contains("metadataRepository.banVisitorResult(islandId, actorUuid, playerUuid, reason)"));
        assertTrue(!routes.contains("metadataRepository.removeMember(islandId, playerUuid)"));
    }

    @Test
    void inMemoryVisitorBanCannotRemoveOrBanAnExistingMember() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID memberUuid = UUID.randomUUID();
        metadata.upsertMemberKey(islandId, memberUuid, "MEMBER");

        assertEquals("VISITOR_BAN_DENIED", metadata.banVisitorResult(islandId, actorUuid, memberUuid, "race"));
        assertEquals(1, metadata.members(islandId).size());
        assertTrue(!metadata.isBanned(islandId, memberUuid));
    }

    @Test
    void visitorPardonUsesTheSameIslandLockAsBan() throws Exception {
        String jdbc = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        int operation = jdbc.indexOf("public String pardonVisitorResult(");
        int commit = jdbc.indexOf("connection.commit();", operation);
        int nextMethod = jdbc.indexOf("\n    @Override", commit);

        assertTrue(operation >= 0 && commit > operation && nextMethod > commit);
        String transaction = jdbc.substring(operation, nextMethod);
        assertTrue(transaction.contains("connection.setAutoCommit(false)"));
        assertTrue(transaction.contains("SELECT id FROM islands"));
        assertTrue(transaction.contains("FOR UPDATE"), "ban and pardon must serialize through the same island row");
        assertTrue(transaction.contains("DELETE FROM island_bans"));
        assertTrue(transaction.contains("statement.executeUpdate() > 0"));
        assertTrue(transaction.contains("return removed ? \"APPLIED\" : \"BAN_NOT_FOUND\""));

        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        UUID visitorUuid = UUID.randomUUID();
        assertEquals("BAN_NOT_FOUND", metadata.pardonVisitorResult(islandId, visitorUuid));
        assertEquals("APPLIED", metadata.banVisitorResult(islandId, actorUuid, visitorUuid, "test"));
        assertEquals("APPLIED", metadata.pardonVisitorResult(islandId, visitorUuid));
        assertEquals("BAN_NOT_FOUND", metadata.pardonVisitorResult(islandId, visitorUuid));
        assertTrue(!metadata.isBanned(islandId, visitorUuid));
    }

    @Test
    void missingPardonCannotPublishAFalseBanChange() throws Exception {
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutes.java"));
        int operation = routes.indexOf("String result = metadataRepository.pardonVisitorResult(islandId, playerUuid)");
        int guard = routes.indexOf("if (!\"APPLIED\".equals(result))", operation);
        int audit = routes.indexOf("ISLAND_VISITOR_PARDON", operation);
        int event = routes.indexOf("ISLAND_VISITOR_BAN_CHANGED", audit);

        assertTrue(operation >= 0 && guard > operation);
        assertTrue(guard < audit && guard < event, "missing bans must return before audit and change events");
        assertTrue(routes.substring(guard, audit).contains("BAN_NOT_FOUND"));
    }

    @Test
    void visitorSanctionsRejectAuthoritativeOwnersAndTeamMembers() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutes.java"));

        assertEquals(2, occurrences(source, "if (teamMemberOrOwner(islandId, playerUuid))"));
        assertTrue(source.contains("island.ownerUuid().equals(playerUuid)"));
        assertTrue(source.contains("CoreRoleKeys.memberRole(memberRoleKey(metadataRepository.members(islandId), playerUuid))"));
        assertTrue(source.contains("VISITOR_BAN_DENIED"));
        assertTrue(source.contains("VISITOR_KICK_DENIED"));
    }

    @Test
    void acceptedInviteInitializesOnlyAnEmptySelectedIsland() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutes.java"));

        assertTrue(source.contains("playerProfiles.find(playerUuid).primaryIslandId().isEmpty()"));
        assertTrue(source.contains("playerProfiles.setPrimaryIsland(playerUuid, invite.get().islandId())"));
    }

    @Test
    void jdbcInviteAcceptanceCommitsMembershipAndInitialPrimaryTogether() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        int accept = source.indexOf("public String acceptInviteResult(UUID inviteId, UUID playerUuid, long maxMembers)");
        int acceptedUpdate = source.indexOf("UPDATE island_invites SET state = 'ACCEPTED'", accept);
        int commit = source.indexOf("connection.commit();", acceptedUpdate);

        assertTrue(accept >= 0 && acceptedUpdate > accept && commit > acceptedUpdate);
        String transaction = source.substring(accept, commit);
        assertTrue(transaction.contains("UPDATE island_invites SET state = 'ACCEPTED'"));
        assertTrue(transaction.contains("acceptInviteMemberSql(connection)"));
        assertTrue(transaction.contains("ensurePlayerProfileSql(connection)"));
        assertTrue(transaction.contains("primary_island_id IS NULL"), "accepting another invite must preserve an existing selected island");
    }

    @Test
    void inviteAcceptanceReturnsTheAuthoritativeTransactionFailure() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandVisitorRoutes.java"));

        assertTrue(source.contains("metadataRepository.acceptInviteResult(inviteId, playerUuid, maxMembers)"));
        assertTrue(source.contains("result.equals(\"MEMBER_LIMIT\")"));
        assertTrue(source.contains("result.equals(\"ISLAND_NOT_FOUND\")"));
        assertTrue(source.contains("result.equals(\"ALREADY_MEMBER\")"));
        assertTrue(source.contains("ApiResponses.error(result, errorMessage)"));
    }

    @Test
    void jdbcInviteReplacementSerializesAndCommitsExpirationWithInsertion() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        int create = source.indexOf("public IslandInviteSnapshot createInvite(");
        int commit = source.indexOf("connection.commit();", create);

        assertTrue(create >= 0 && commit > create);
        String transaction = source.substring(create, commit);
        assertTrue(transaction.contains("connection.setAutoCommit(false)"));
        assertTrue(transaction.contains("SELECT id FROM islands"));
        assertTrue(transaction.contains("FOR UPDATE"), "concurrent replacements must serialize before expiring the pending invite");
        assertTrue(transaction.contains("UPDATE island_invites SET state = 'EXPIRED'"));
        assertTrue(transaction.contains("INSERT INTO island_invites"));
    }

    @Test
    void expiredInviteCannotBeDeclinedAsIfItWereStillUsable() throws Exception {
        String jdbc = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String memory = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/InMemoryIslandMetadataRepository.java"));
        int decline = jdbc.indexOf("public boolean declineInvite(");
        int nextMethod = jdbc.indexOf("\n    @Override", decline + 20);
        String transaction = jdbc.substring(decline, nextMethod);

        assertTrue(transaction.contains("!invite.expiresAt().isAfter(Instant.now())"));
        assertTrue(transaction.contains("UPDATE island_invites SET state = 'EXPIRED'"));
        assertTrue(transaction.indexOf("state = 'EXPIRED'") < transaction.indexOf("state = 'DECLINED'"));
        assertTrue(transaction.contains("connection.commit();"), "expired state cleanup must commit while decline still returns false");
        assertTrue(memory.contains("public synchronized boolean declineInvite("));
        assertTrue(memory.contains("\"EXPIRED\", invite.createdAt(), invite.expiresAt()"));
    }

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
    void registersIslandVisitorEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandVisitorRoutes(null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/invites"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/invites"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/invites/accept"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/invites/decline"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/bans/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/bans"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/bans/remove"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/visitors/kick"));
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
        IslandMemberSnapshot member = new IslandMemberSnapshot(islandId, bannedUuid, "BANNED", Instant.parse("2026-01-02T03:04:05Z"), null);
        IslandMemberSnapshot builder = new IslandMemberSnapshot(islandId, actorUuid, "BUILDER", Instant.parse("2026-01-02T03:04:05Z"), null);

        assertEquals("BANNED", IslandVisitorRoutes.memberRoleKey(List.of(member), bannedUuid));
        assertEquals("BUILDER", IslandVisitorRoutes.memberRoleKey(List.of(builder), actorUuid));
        assertNull(IslandVisitorRoutes.memberRoleKey(List.of(member), actorUuid));
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

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
