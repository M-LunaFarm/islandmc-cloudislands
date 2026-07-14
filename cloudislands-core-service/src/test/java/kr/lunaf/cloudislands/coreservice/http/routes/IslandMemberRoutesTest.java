package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import org.junit.jupiter.api.Test;

class IslandMemberRoutesTest {
    @Test
    void registersIslandMemberEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandMemberRoutes routes = new IslandMemberRoutes(null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(11, paths.size());
        assertTrue(paths.contains("/v1/islands/members"));
        assertTrue(paths.contains("/v1/players/islands"));
        assertTrue(paths.contains("/v1/islands/members/set"));
        assertTrue(paths.contains("/v1/islands/members/trust-temporary"));
        assertTrue(paths.contains("/v1/islands/transfer"));
        assertTrue(paths.contains("/v1/islands/members/remove"));
        assertTrue(paths.contains("/v1/admin/islands/members/add"));
        assertTrue(paths.contains("/v1/admin/islands/members/kick"));
        assertTrue(paths.contains("/v1/admin/islands/members/promote"));
        assertTrue(paths.contains("/v1/admin/islands/members/demote"));
        assertTrue(paths.contains("/v1/admin/islands/members/setleader"));
    }

    @Test
    void registersIslandMemberEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandMemberRoutes(null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/members"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/islands"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/members/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/members/trust-temporary"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/transfer"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/members/remove"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/members/add"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/members/kick"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/members/promote"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/members/demote"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/members/setleader"));
    }

    @Test
    void adminMemberRoutesMutateMembersWithoutIslandPermission() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000102");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000103");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "member-admin");
        profiles.setPrimaryIsland(ownerUuid, islandId);

        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            profiles,
            new InMemoryIslandLogRepository(),
            audit,
            events
        ).register(handlers::put);

        handle(handlers, "/v1/admin/islands/members/add", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\",\"roleKey\":\"MEMBER\"}", 202, "MEMBER_ADDED");
        assertEquals("MEMBER", metadata.members(islandId).get(0).effectiveRoleKey());
        assertEquals(islandId, profiles.find(playerUuid).primaryIslandId().orElseThrow());
        handle(handlers, "/v1/admin/islands/members/promote", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "MEMBER_PROMOTED");
        assertEquals("MODERATOR", metadata.members(islandId).get(0).effectiveRoleKey());
        handle(handlers, "/v1/admin/islands/members/demote", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "MEMBER_DEMOTED");
        assertEquals("MEMBER", metadata.members(islandId).get(0).effectiveRoleKey());
        handle(handlers, "/v1/admin/islands/members/kick", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "MEMBER_KICKED");
        assertTrue(metadata.members(islandId).isEmpty());
        assertTrue(profiles.find(playerUuid).primaryIslandId().isEmpty(), "a removed member must not retain the removed island as their selected island");
        handle(handlers, "/v1/admin/islands/members/setleader", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "LEADER_SET");
        assertEquals(playerUuid, islands.findById(islandId).orElseThrow().ownerUuid());
        assertEquals(islandId, profiles.find(ownerUuid).primaryIslandId().orElseThrow(), "the former owner remains a co-owner and must keep their selected island");
        assertEquals(islandId, profiles.find(playerUuid).primaryIslandId().orElseThrow());
        assertEquals(1L, events.countByType("ISLAND_OWNERSHIP_CHANGED"));
        assertTrue(audit.toJson().contains("ISLAND_MEMBER_ADMIN_ADD"));
        assertTrue(audit.toJson().contains("ISLAND_MEMBER_ADMIN_KICK"));
        assertTrue(audit.toJson().contains("ISLAND_MEMBER_ADMIN_SETLEADER"));
    }

    @Test
    void identicalPermanentRoleWritesAreIdempotent() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000105");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000106");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000107");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "idempotent-role-write");

        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            audit,
            events
        ).register(handlers::put);

        String body = memberBody(islandId, playerUuid, "MEMBER");
        handle(handlers, "/v1/admin/islands/members/add", body, 202, "MEMBER_ADDED");
        handle(handlers, "/v1/admin/islands/members/add", body, 200, "MEMBER_UNCHANGED");

        assertEquals(1L, events.countByType("ISLAND_MEMBER_JOINED"));
        assertEquals(0L, events.countByType("ISLAND_MEMBER_ROLE_CHANGED"));
        assertEquals(1, occurrences(audit.toJson(), "ISLAND_MEMBER_ADMIN_ADD"));
    }

    @Test
    void inMemoryLimitedUpsertsMatchAuthoritativeResults() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID islandId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals("APPLIED", metadata.upsertMemberKeyAndInitializePrimary(islandId, first, "MEMBER", 1L, 1L));
        assertEquals("UNCHANGED", metadata.upsertMemberKeyAndInitializePrimary(islandId, first, "MEMBER", 1L, 1L));
        assertEquals("MEMBER_LIMIT", metadata.upsertMemberKeyAndInitializePrimary(islandId, second, "MEMBER", 1L, 1L));

        UUID roleIsland = UUID.randomUUID();
        assertEquals("APPLIED", metadata.upsertMemberKeyWithRoleLimit(roleIsland, first, "MODERATOR", null, 1L));
        assertEquals("UNCHANGED", metadata.upsertMemberKeyWithRoleLimit(roleIsland, first, "MODERATOR", null, 1L));
        assertEquals("ROLE_LIMIT", metadata.upsertMemberKeyWithRoleLimit(roleIsland, second, "MODERATOR", null, 1L));
    }

    @Test
    void authoritativeOwnerCannotSelfRemoveWhenMembershipProjectionIsMissing() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000121");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000122");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "owner-projection-test");
        profiles.setPrimaryIsland(ownerUuid, islandId);
        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            profiles,
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            new InMemoryGlobalEventPublisher()
        ).register(handlers::put);

        handleError(handlers, "/v1/islands/members/remove", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + ownerUuid + "\",\"actorUuid\":\"" + ownerUuid + "\"}", 409, "OWNER_ROLE_PROTECTED");

        assertEquals(islandId, profiles.find(ownerUuid).primaryIslandId().orElseThrow());
    }

    @Test
    void missingMemberRemovalDoesNotPublishGhostLeaveEvents() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000125");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000126");
        UUID missingUuid = UUID.fromString("00000000-0000-0000-0000-000000000127");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "truthful-member-removal");

        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            audit,
            events
        ).register(handlers::put);

        String selfRemoval = "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + missingUuid + "\",\"actorUuid\":\"" + missingUuid + "\"}";
        String adminRemoval = "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + missingUuid + "\"}";
        handleError(handlers, "/v1/islands/members/remove", selfRemoval, 404, "MEMBER_NOT_FOUND");
        handleError(handlers, "/v1/admin/islands/members/kick", adminRemoval, 404, "MEMBER_NOT_FOUND");

        assertEquals(0L, events.countByType("ISLAND_MEMBER_LEFT"));
        assertEquals(0L, events.countByType("ISLAND_MEMBER_CHANGED"));
        assertFalse(audit.toJson().contains("ISLAND_MEMBER_REMOVE"));
        assertFalse(audit.toJson().contains("ISLAND_MEMBER_ADMIN_KICK"));
    }

    @Test
    void ownershipTransferNeverClearsTheFormerOwnersSelectedIsland() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutes.java"));

        assertFalse(source.contains("playerProfiles.clearPrimaryIsland(actorUuid)"));
        assertFalse(source.contains("playerProfiles.clearPrimaryIsland(oldOwner)"));
    }

    @Test
    void jdbcOwnershipTransferCommitsNewOwnerPrimaryIslandAtomically() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandRepository.java"));
        int transfer = source.indexOf("public boolean transferOwnership(");
        int commit = source.indexOf("connection.commit();", transfer);
        int primary = source.indexOf("setPrimaryIsland(connection, newOwnerUuid, islandId);", transfer);

        assertTrue(transfer >= 0);
        assertTrue(primary > transfer && primary < commit, "new owner primary island must update before the ownership transaction commits");
        assertTrue(source.substring(transfer, commit).contains("coOwnerMemberUpsertSql(connection)"), "former owner must remain a co-owner even when its membership projection was missing");
    }

    @Test
    void jdbcMemberRemovalClearsOnlyTheRemovedIslandPrimaryInTheSameTransaction() throws Exception {
        String metadata = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutes.java"));
        int operation = metadata.indexOf("public boolean removeMemberAndClearPrimaryResult(");
        int commit = metadata.indexOf("connection.commit();", operation);

        assertTrue(operation >= 0 && commit > operation);
        String transaction = metadata.substring(operation, commit);
        assertTrue(transaction.contains("DELETE FROM island_members"));
        assertTrue(transaction.contains("primary_island_id = NULL"));
        assertTrue(transaction.contains("primary_island_id = ?"), "removing another island must not clear an unrelated selected island");
        assertTrue(transaction.contains("NOT EXISTS (SELECT 1 FROM islands"), "an authoritative owner must never lose its selected island through metadata cleanup");
        assertTrue(transaction.contains("boolean removed = member.executeUpdate() > 0"), "the transaction must report whether a membership row was actually removed");
        assertEquals(2, routes.split(java.util.regex.Pattern.quote("metadataRepository.removeMemberAndClearPrimaryResult(islandId, playerUuid)"), -1).length - 1);
    }

    @Test
    void jdbcDirectTeamAddInitializesOnlyAnEmptyPrimaryInTheSameTransaction() throws Exception {
        String repository = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutes.java"));
        int operation = repository.indexOf("public void upsertMemberKeyAndInitializePrimary(");
        int commit = repository.indexOf("connection.commit();", operation);

        assertTrue(operation >= 0 && commit > operation);
        String transaction = repository.substring(operation, commit);
        assertTrue(transaction.contains("upsertMemberSql(connection)"));
        assertTrue(transaction.contains("ensurePlayerProfileSql(connection)"));
        assertTrue(transaction.contains("primary_island_id IS NULL"));
        assertEquals(3, routes.split(java.util.regex.Pattern.quote("metadataRepository.upsertMemberKeyAndInitializePrimary("), -1).length - 1);
        assertTrue(transaction.contains("FOR UPDATE"), "concurrent joins must serialize on the island row");
        assertTrue(transaction.contains("teamMemberCount(connection, islandId)"));
        assertTrue(transaction.contains("roleMemberCount(connection, islandId, normalizedRoleKey)"));
    }

    @Test
    void jdbcRoleChangesEnforceRoleLimitsInsideTheIslandTransaction() throws Exception {
        String repository = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutes.java"));
        int operation = repository.indexOf("public String upsertMemberKeyWithRoleLimit(");
        int commit = repository.indexOf("connection.commit();", operation);
        int nextMethod = repository.indexOf("\n    @Override", commit);

        assertTrue(operation >= 0 && commit > operation && nextMethod > commit);
        String transaction = repository.substring(operation, nextMethod);
        assertTrue(transaction.contains("SELECT id FROM islands"));
        assertTrue(transaction.contains("FOR UPDATE"), "concurrent role changes must serialize on the island row");
        assertTrue(transaction.contains("currentMemberState(connection, islandId, playerUuid)"));
        assertTrue(transaction.contains("roleMemberCount(connection, islandId, normalizedRoleKey)"));
        assertTrue(transaction.contains("!normalizedRoleKey.equals(currentRole)"), "renewing the same temporary role must not consume another slot");
        assertTrue(transaction.contains("return unchanged ? \"UNCHANGED\" : \"APPLIED\""));
        assertEquals(4, routes.split(java.util.regex.Pattern.quote("metadataRepository.upsertMemberKeyWithRoleLimit("), -1).length - 1);
    }

    @Test
    void roleLimitBlocksAdminAddAndPromotionIntoLimitedRole() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000111");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000112");
        UUID firstUuid = UUID.fromString("00000000-0000-0000-0000-000000000113");
        UUID secondUuid = UUID.fromString("00000000-0000-0000-0000-000000000114");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "role-limit");
        limits.set(islandId, GameplayParityPolicy.roleLimitKey("MODERATOR"), 1L, ownerUuid);

        new IslandMemberRoutes(
            islands,
            metadata,
            limits,
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            new InMemoryGlobalEventPublisher()
        ).register(handlers::put);

        handle(handlers, "/v1/admin/islands/members/add", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + firstUuid + "\",\"roleKey\":\"MODERATOR\"}", 202, "MEMBER_ADDED");
        handleError(handlers, "/v1/admin/islands/members/add", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + secondUuid + "\",\"roleKey\":\"MODERATOR\"}", 409, "ROLE_LIMIT");
        handle(handlers, "/v1/admin/islands/members/add", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + secondUuid + "\",\"roleKey\":\"MEMBER\"}", 202, "MEMBER_ADDED");
        handleError(handlers, "/v1/admin/islands/members/promote", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + secondUuid + "\"}", 409, "ROLE_LIMIT");

        assertEquals("MODERATOR", metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(firstUuid)).findFirst().orElseThrow().effectiveRoleKey());
        assertEquals("MEMBER", metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(secondUuid)).findFirst().orElseThrow().effectiveRoleKey());
    }

    @Test
    void temporaryTrustPreservesPermanentMembersAndCanBeRenewed() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000121");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000122");
        UUID memberUuid = UUID.fromString("00000000-0000-0000-0000-000000000123");
        UUID coopUuid = UUID.fromString("00000000-0000-0000-0000-000000000124");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "temporary-trust-integrity");

        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            new InMemoryGlobalEventPublisher()
        ).register(handlers::put);

        handle(handlers, "/v1/admin/islands/members/add", memberBody(islandId, memberUuid, "MEMBER"), 202, "MEMBER_ADDED");
        handleError(handlers, "/v1/islands/members/trust-temporary", temporaryTrustBody(islandId, ownerUuid, memberUuid), 409, "ALREADY_ISLAND_MEMBER");
        IslandMemberSnapshot permanentMember = metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(memberUuid)).findFirst().orElseThrow();
        assertEquals("MEMBER", permanentMember.effectiveRoleKey());
        assertNull(permanentMember.expiresAt());

        handleAccepted(handlers, "/v1/islands/members/trust-temporary", temporaryTrustBody(islandId, ownerUuid, coopUuid), 202);
        Instant firstExpiry = metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(coopUuid)).findFirst().orElseThrow().expiresAt();
        handleAccepted(handlers, "/v1/islands/members/trust-temporary", temporaryTrustBody(islandId, ownerUuid, coopUuid), 202);
        IslandMemberSnapshot renewed = metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(coopUuid)).findFirst().orElseThrow();
        assertEquals("TRUSTED", renewed.effectiveRoleKey());
        assertTrue(!renewed.expiresAt().isBefore(firstExpiry));
    }

    @Test
    void coopCapacityIsIndependentFromPermanentTeamCapacity() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000131");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000132");
        UUID memberUuid = UUID.fromString("00000000-0000-0000-0000-000000000133");
        UUID coopUuid = UUID.fromString("00000000-0000-0000-0000-000000000134");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "coop-capacity");
        limits.set(islandId, "MEMBERS", 1L, ownerUuid);
        limits.set(islandId, GameplayParityPolicy.roleLimitKey("TRUSTED"), 1L, ownerUuid);

        new IslandMemberRoutes(
            islands,
            metadata,
            limits,
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            events
        ).register(handlers::put);

        handle(handlers, "/v1/admin/islands/members/add", memberBody(islandId, memberUuid, "MEMBER"), 202, "MEMBER_ADDED");
        handleAccepted(handlers, "/v1/islands/members/set", setRoleBody(islandId, ownerUuid, coopUuid, "TRUSTED"), 202);
        handleError(handlers, "/v1/islands/members/set", setRoleBody(islandId, ownerUuid, memberUuid, "TRUSTED"), 409, "ALREADY_ISLAND_MEMBER");
        handleError(handlers, "/v1/islands/members/set", setRoleBody(islandId, ownerUuid, coopUuid, "MEMBER"), 409, "MEMBER_LIMIT");

        assertEquals("MEMBER", metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(memberUuid)).findFirst().orElseThrow().effectiveRoleKey());
        assertEquals("TRUSTED", metadata.members(islandId).stream().filter(member -> member.playerUuid().equals(coopUuid)).findFirst().orElseThrow().effectiveRoleKey());
        assertEquals(1L, events.countByType("ISLAND_COOP_ADDED"));
        assertEquals(0L, events.countByType("ISLAND_COOP_REMOVED"));
    }

    @Test
    void defaultCoopCapacityMatchesSuperiorSkyblockWithoutConfiguration() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000161");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000162");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "default-coop-capacity");

        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            new InMemoryGlobalEventPublisher()
        ).register(handlers::put);

        for (int index = 1; index <= 8; index++) {
            UUID coopUuid = new UUID(0L, 0x170L + index);
            handleAccepted(handlers, "/v1/islands/members/set", setRoleBody(islandId, ownerUuid, coopUuid, "TRUSTED"), 202);
        }
        UUID ninthCoop = new UUID(0L, 0x179L);
        handleError(handlers, "/v1/islands/members/set", setRoleBody(islandId, ownerUuid, ninthCoop, "TRUSTED"), 409, "ROLE_LIMIT");
        assertEquals(8L, metadata.members(islandId).stream().filter(member -> member.effectiveRoleKey().equals("TRUSTED")).count());
    }

    @Test
    void rendersMemberContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");

        IslandMemberSnapshot member = new IslandMemberSnapshot(islandId, playerUuid, "CO_OWNER", Instant.parse("2026-01-02T03:04:05Z"), null);
        IslandMemberSnapshot temporary = new IslandMemberSnapshot(islandId, UUID.fromString("00000000-0000-0000-0000-000000000004"), "TRUSTED", Instant.parse("2026-01-02T04:04:05Z"), Instant.parse("2026-01-02T05:04:05Z"));

        assertEquals("CO_OWNER", IslandMemberRoutes.memberRoleKey(List.of(member), playerUuid));
        assertNull(IslandMemberRoutes.memberRoleKey(List.of(member), UUID.fromString("00000000-0000-0000-0000-000000000003")));
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

        assertEquals("BUILDER", IslandMemberRoutes.memberRoleKey(List.of(member), playerUuid));
        assertEquals("BUILDER", member.effectiveRoleKey());
        Map<?, ?> members = SimpleJson.object(SimpleJson.parse(IslandMemberRoutes.membersJson(List.of(member))));
        assertMember(islandId, playerUuid, "BUILDER", SimpleJson.object(SimpleJson.list(members.get("members")).get(0)));
    }

    @Test
    void selfLeaveBypassesManageMembersPermissionButOwnerRemainsProtected() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandMemberRoutes.java"));

        assertTrue(source.contains("isOwner(islandId, playerUuid)"), "owners must remain protected by the authoritative island record even if membership projection is stale");
        assertTrue(source.contains("!actorUuid.equals(playerUuid) && !requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)"), "self-leave must not require MANAGE_MEMBERS");
        assertTrue(source.contains("clearPrimaryIslandIfSelected(islandId, playerUuid)"), "member removal must clear only a selected island that is no longer accessible");
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

    @Test
    void playerIslandContractsUseAuthoritativeBorderLimit() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000022");
        IslandSnapshot island = new IslandSnapshot(
            islandId, ownerUuid, "Border upgrade", IslandState.ACTIVE, 300, 0L, "0", true, Instant.EPOCH, Instant.EPOCH
        );
        kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository limits = new kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository();
        limits.set(islandId, "BORDER", 500L, ownerUuid);

        Map<?, ?> islands = SimpleJson.object(SimpleJson.parse(IslandMemberRoutes.islandsJson(List.of(island), limits)));
        Map<?, ?> rendered = SimpleJson.object(SimpleJson.list(islands.get("islands")).get(0));

        assertEquals(300, ((Number) rendered.get("size")).intValue());
        assertEquals(500L, ((Number) rendered.get("border")).longValue());
    }

    @Test
    void playerIslandsRoutePreservesMembershipRoleForClients() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000202");
        UUID coopUuid = UUID.fromString("00000000-0000-0000-0000-000000000203");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        Map<String, HttpHandler> handlers = new HashMap<>();
        Instant expiresAt = Instant.now().plusSeconds(3600L);
        islands.createOwnedIsland(islandId, ownerUuid, "default", "role-preserving-navigation");
        metadata.upsertMemberKey(islandId, coopUuid, "TRUSTED", expiresAt);

        new IslandMemberRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryPlayerProfileRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            new InMemoryGlobalEventPublisher()
        ).register(handlers::put);

        TestExchange exchange = new TestExchange("/v1/players/islands", "{\"playerUuid\":\"" + coopUuid + "\"}");
        handlers.get("/v1/players/islands").handle(exchange);
        assertEquals(200, exchange.status());
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(exchange.body()));
        Map<?, ?> rendered = SimpleJson.object(SimpleJson.list(root.get("islands")).get(0));
        assertEquals("TRUSTED", SimpleJson.text(rendered.get("role")));
        assertEquals("TRUSTED", SimpleJson.text(rendered.get("roleKey")));
        assertEquals(expiresAt.toString(), SimpleJson.text(rendered.get("membershipExpiresAt")));

        metadata.upsertMemberKey(islandId, coopUuid, "BANNED");
        TestExchange bannedExchange = new TestExchange("/v1/players/islands", "{\"playerUuid\":\"" + coopUuid + "\"}");
        handlers.get("/v1/players/islands").handle(bannedExchange);
        assertTrue(SimpleJson.list(SimpleJson.object(SimpleJson.parse(bannedExchange.body())).get("islands")).isEmpty());

        TestExchange ownerExchange = new TestExchange("/v1/players/islands", "{\"playerUuid\":\"" + ownerUuid + "\"}");
        handlers.get("/v1/players/islands").handle(ownerExchange);
        Map<?, ?> ownerIsland = SimpleJson.object(SimpleJson.list(SimpleJson.object(SimpleJson.parse(ownerExchange.body())).get("islands")).get(0));
        assertEquals("OWNER", SimpleJson.text(ownerIsland.get("roleKey")), "authoritative ownership must remain visible without a membership projection");
    }

    private static void assertMember(UUID islandId, UUID playerUuid, String roleKey, Map<?, ?> member) {
        assertEquals(islandId.toString(), SimpleJson.text(member.get("islandId")));
        assertEquals(playerUuid.toString(), SimpleJson.text(member.get("playerUuid")));
        assertEquals(roleKey, SimpleJson.text(member.get("role")));
        assertEquals(roleKey, SimpleJson.text(member.get("roleKey")));
        assertTrue(!SimpleJson.text(member.get("joinedAt")).isBlank());
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
    }

    private static void handle(Map<String, HttpHandler> handlers, String path, String body, int expectedStatus, String expectedCode) throws Exception {
        TestExchange exchange = new TestExchange(path, body);
        handlers.get(path).handle(exchange);
        assertEquals(expectedStatus, exchange.status());
        Map<?, ?> response = SimpleJson.object(SimpleJson.parse(exchange.body()));
        assertEquals(true, response.get("accepted"));
        assertEquals(expectedCode, SimpleJson.text(response.get("code")));
    }

    private static void handleError(Map<String, HttpHandler> handlers, String path, String body, int expectedStatus, String expectedCode) throws Exception {
        TestExchange exchange = new TestExchange(path, body);
        handlers.get(path).handle(exchange);
        assertEquals(expectedStatus, exchange.status());
        Map<?, ?> response = SimpleJson.object(SimpleJson.parse(exchange.body()));
        Map<?, ?> error = SimpleJson.object(response.get("error"));
        assertEquals(expectedCode, SimpleJson.text(error.get("code")));
    }

    private static void handleAccepted(Map<String, HttpHandler> handlers, String path, String body, int expectedStatus) throws Exception {
        TestExchange exchange = new TestExchange(path, body);
        handlers.get(path).handle(exchange);
        assertEquals(expectedStatus, exchange.status());
        assertEquals(true, SimpleJson.object(SimpleJson.parse(exchange.body())).get("accepted"));
    }

    private static String memberBody(UUID islandId, UUID playerUuid, String roleKey) {
        return "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\",\"roleKey\":\"" + roleKey + "\"}";
    }

    private static String temporaryTrustBody(UUID islandId, UUID actorUuid, UUID playerUuid) {
        return "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"durationSeconds\":3600}";
    }

    private static String setRoleBody(UUID islandId, UUID actorUuid, UUID playerUuid, String roleKey) {
        return "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + actorUuid + "\",\"playerUuid\":\"" + playerUuid + "\",\"roleKey\":\"" + roleKey + "\"}";
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

    private static final class TestExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayInputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final URI uri;
        private int status;

        private TestExchange(String path, String body) {
            this.uri = URI.create(path);
            this.requestBody = new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return uri;
        }

        @Override
        public String getRequestMethod() {
            return "POST";
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int status, long responseLength) {
            this.status = status;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream input, OutputStream output) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        int status() {
            return status;
        }

        String body() {
            return responseBody.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
