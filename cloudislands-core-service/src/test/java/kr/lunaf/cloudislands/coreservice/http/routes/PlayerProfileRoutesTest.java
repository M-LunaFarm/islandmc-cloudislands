package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys;
import org.junit.jupiter.api.Test;

class PlayerProfileRoutesTest {
    @Test
    void registersPlayerProfileEndpointGroup() {
        List<String> paths = new ArrayList<>();
        PlayerProfileRoutes routes = new PlayerProfileRoutes(null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(9, paths.size());
        assertTrue(paths.contains("/v1/admin/players/info"));
        assertTrue(paths.contains("/v1/players/info"));
        assertTrue(paths.contains("/v1/players/touch"));
        assertTrue(paths.contains("/v1/players/locale"));
        assertTrue(paths.contains("/v1/players/select-island"));
        assertTrue(paths.contains("/v1/admin/players/setisland"));
        assertTrue(paths.contains("/v1/admin/players/clearisland"));
        assertTrue(paths.contains("/v1/admin/players/setdisbands"));
        assertTrue(paths.contains("/v1/admin/players/adddisbands"));
    }

    @Test
    void registersPlayerProfileEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new PlayerProfileRoutes(null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/touch"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/locale"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/select-island"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/setisland"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/clearisland"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/setdisbands"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/players/adddisbands"));
    }

    @Test
    void playerProfileJsonIncludesLocale() {
        String json = PlayerProfileRoutes.playerProfileJson(new kr.lunaf.cloudislands.api.model.PlayerIslandProfile(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Steve, \"Builder\"",
            java.util.Optional.empty(),
            java.time.Instant.EPOCH,
            "EN-US",
            3
        ));
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));

        assertEquals("Steve, \"Builder\"", SimpleJson.text(root.get("lastName")));
        assertNull(root.get("primaryIslandId"));
        assertEquals("en_us", SimpleJson.text(root.get("locale")));
        assertEquals(3L, SimpleJson.number(root.get("disbandsRemaining")));
    }

    @Test
    void selectingPrimaryIslandRequiresAuthoritativeMembership() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID memberUuid = UUID.fromString("00000000-0000-0000-0000-000000000303");
        UUID bannedUuid = UUID.fromString("00000000-0000-0000-0000-000000000304");
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Selection Test");
        metadata.upsertMemberKey(islandId, memberUuid, CoreRoleKeys.MEMBER);
        metadata.upsertMemberKey(islandId, bannedUuid, CoreRoleKeys.BANNED);
        Map<String, HttpHandler> handlers = new HashMap<>();
        new PlayerProfileRoutes(profiles, islands, metadata, audit).register(handlers::put);

        TestExchange owner = exchange(ownerUuid, islandId);
        handlers.get("/v1/players/select-island").handle(owner);
        TestExchange member = exchange(memberUuid, islandId);
        handlers.get("/v1/players/select-island").handle(member);
        TestExchange banned = exchange(bannedUuid, islandId);
        handlers.get("/v1/players/select-island").handle(banned);

        assertEquals(202, owner.status());
        assertEquals(islandId, profiles.find(ownerUuid).primaryIslandId().orElseThrow());
        assertEquals(202, member.status());
        assertEquals(islandId, profiles.find(memberUuid).primaryIslandId().orElseThrow());
        assertEquals(403, banned.status());
        assertTrue(banned.body().contains("ISLAND_SELECTION_DENIED"));
        assertTrue(profiles.find(bannedUuid).primaryIslandId().isEmpty());
        assertTrue(audit.toJson().contains("PLAYER_SELECT_ISLAND"));
    }

    private static TestExchange exchange(UUID playerUuid, UUID islandId) {
        return new TestExchange("{\"playerUuid\":\"" + playerUuid + "\",\"islandId\":\"" + islandId + "\"}");
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
        private int status;

        private TestExchange(String body) {
            requestBody = new ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return URI.create("/test"); }
        @Override public String getRequestMethod() { return "POST"; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() { }
        @Override public InputStream getRequestBody() { return requestBody; }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int status, long responseLength) throws IOException { this.status = status; }
        @Override public InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 25565); }
        @Override public int getResponseCode() { return status; }
        @Override public InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 8080); }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) { }
        @Override public void setStreams(InputStream input, OutputStream output) { }
        @Override public HttpPrincipal getPrincipal() { return null; }
        private int status() { return status; }
        private String body() { return responseBody.toString(java.nio.charset.StandardCharsets.UTF_8); }
    }
}
