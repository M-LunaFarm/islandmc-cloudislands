package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        handle(handlers, "/v1/admin/islands/members/promote", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "MEMBER_PROMOTED");
        assertEquals("MODERATOR", metadata.members(islandId).get(0).effectiveRoleKey());
        handle(handlers, "/v1/admin/islands/members/demote", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "MEMBER_DEMOTED");
        assertEquals("MEMBER", metadata.members(islandId).get(0).effectiveRoleKey());
        handle(handlers, "/v1/admin/islands/members/kick", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "MEMBER_KICKED");
        assertTrue(metadata.members(islandId).isEmpty());
        handle(handlers, "/v1/admin/islands/members/setleader", "{\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\"}", 202, "LEADER_SET");
        assertEquals(playerUuid, islands.findById(islandId).orElseThrow().ownerUuid());
        assertEquals(1L, events.countByType("ISLAND_OWNERSHIP_CHANGED"));
        assertTrue(audit.toJson().contains("ISLAND_MEMBER_ADMIN_ADD"));
        assertTrue(audit.toJson().contains("ISLAND_MEMBER_ADMIN_KICK"));
        assertTrue(audit.toJson().contains("ISLAND_MEMBER_ADMIN_SETLEADER"));
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

        assertTrue(source.contains("member.effectiveRoleKey().equals(CoreRoleKeys.OWNER)"), "owners must remain protected from member removal");
        assertTrue(source.contains("!actorUuid.equals(playerUuid) && !requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)"), "self-leave must not require MANAGE_MEMBERS");
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
