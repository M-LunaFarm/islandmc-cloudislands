package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.role.InMemoryIslandRoleRepository;
import org.junit.jupiter.api.Test;

class PermissionRoleRoutesTest {
    @Test
    void registersPermissionAndRoleEndpointGroup() {
        List<String> paths = new ArrayList<>();
        PermissionRoleRoutes routes = new PermissionRoleRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/islands/permissions"));
        assertTrue(paths.contains("/v1/islands/permissions/set"));
        assertTrue(paths.contains("/v1/islands/permissions/overrides/set"));
        assertTrue(paths.contains("/v1/islands/roles"));
        assertTrue(paths.contains("/v1/islands/roles/upsert"));
        assertTrue(paths.contains("/v1/islands/roles/reset"));
    }

    @Test
    void registersPermissionAndRoleEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new PermissionRoleRoutes(null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/permissions"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/permissions/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/permissions/overrides/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/roles"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/roles/upsert"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/roles/reset"));
    }

    @Test
    void rendersPermissionAndRoleContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        String version = PermissionRoleRoutes.permissionVersion(
            List.of(new IslandPermissionRuleSnapshot(islandId, IslandRole.MEMBER, IslandPermission.BUILD, true)),
            List.of(new IslandPermissionOverrideSnapshot(islandId, playerUuid, IslandPermission.BREAK, false))
        );

        assertEquals(
            "{\"version\":\"" + version + "\",\"rules\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"MEMBER\",\"roleKey\":\"MEMBER\",\"permission\":\"BUILD\",\"allowed\":true}],\"overrides\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"playerUuid\":\"00000000-0000-0000-0000-000000000002\",\"permission\":\"BREAK\",\"allowed\":false}]}",
            PermissionRoleRoutes.permissionsJson(List.of(new IslandPermissionRuleSnapshot(islandId, IslandRole.MEMBER, IslandPermission.BUILD, true)), List.of(new IslandPermissionOverrideSnapshot(islandId, playerUuid, IslandPermission.BREAK, false)))
        );
        assertEquals(
            "{\"roles\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\",\"weight\":7,\"displayName\":\"Builder \\\"A\\\"\"}]}",
            PermissionRoleRoutes.rolesJson(List.of(new IslandRoleSnapshot(islandId, "builder", 7, "Builder \"A\"")))
        );
        assertEquals(
            "{\"roles\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"role\":\"BUILDER\",\"roleKey\":\"BUILDER\",\"weight\":20,\"displayName\":\"Builder\"}]}",
            PermissionRoleRoutes.rolesJson(List.of(new IslandRoleSnapshot(islandId, "builder", 20, "Builder")))
        );
    }

    @Test
    void staleExpectedPermissionVersionRejectsConcurrentAdminSave() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000102");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandPermissionRuleRepository permissions = new InMemoryIslandPermissionRuleRepository();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "owner-island");
        islands.setState(islandId, IslandState.INACTIVE_READY);
        metadata.upsertMember(islandId, ownerUuid, IslandRole.OWNER);
        PermissionRoleRoutes routes = new PermissionRoleRoutes(
            islands,
            metadata,
            permissions,
            new InMemoryIslandRoleRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            (_eventType, _fields) -> {
            }
        );
        Map<String, HttpHandler> handlers = new HashMap<>();
        routes.register(handlers::put);
        String initialVersion = PermissionRoleRoutes.permissionVersion(permissions.list(islandId), permissions.listPlayerOverrides(islandId));

        TestExchange first = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"roleKey\":\"BUILDER\",\"permission\":\"BUILD\",\"allowed\":true,\"expectedVersion\":\"" + initialVersion + "\"}");
        handlers.get("/v1/islands/permissions/set").handle(first);
        TestExchange stale = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"roleKey\":\"BUILDER\",\"permission\":\"BUILD\",\"allowed\":false,\"expectedVersion\":\"" + initialVersion + "\"}");
        handlers.get("/v1/islands/permissions/set").handle(stale);

        assertEquals(202, first.status());
        assertTrue(first.body().contains("\"accepted\":true"));
        assertEquals(409, stale.status());
        assertTrue(stale.body().contains("\"code\":\"PERMISSION_VERSION_CONFLICT\""));
        assertTrue(permissions.allowedRoleKey(islandId, ownerUuid, "BUILDER", IslandPermission.BUILD));
    }

    private TestExchange exchange(String body) {
        return new TestExchange(body);
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
            return URI.create("/test");
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
        public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            this.status = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 25565);
        }

        @Override
        public int getResponseCode() {
            return status;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("127.0.0.1", 8080);
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
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private int status() {
            return status;
        }

        private String body() {
            return responseBody.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }
}
