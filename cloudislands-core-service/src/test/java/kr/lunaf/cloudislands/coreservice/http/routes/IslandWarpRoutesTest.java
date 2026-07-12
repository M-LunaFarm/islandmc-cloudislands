package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import org.junit.jupiter.api.Test;

class IslandWarpRoutesTest {
    @Test
    void homeAndWarpLimitsAreEnforcedInsideTheIslandTransaction() throws Exception {
        String jdbc = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandWarpRoutes.java"));
        int home = jdbc.indexOf("public String upsertHomeWithLimit(");
        int homeCommit = jdbc.indexOf("connection.commit();", home);
        int warp = jdbc.indexOf("public String upsertWarpWithLimit(");
        int warpCommit = jdbc.indexOf("connection.commit();", warp);

        assertTrue(home >= 0 && homeCommit > home);
        assertTrue(warp >= 0 && warpCommit > warp);
        assertTrue(jdbc.substring(home, homeCommit).contains("lockIslandForLimitedResource(connection, islandId)"));
        assertTrue(jdbc.substring(home, homeCommit).contains("namedResourceCount(connection, \"island_homes\", islandId)"));
        assertTrue(jdbc.substring(warp, warpCommit).contains("lockIslandForLimitedResource(connection, islandId)"));
        assertTrue(jdbc.substring(warp, warpCommit).contains("namedResourceCount(connection, \"island_warps\", islandId)"));
        assertTrue(routes.contains("metadataRepository.upsertHomeWithLimit("));
        assertTrue(routes.contains("limitValue(islandId, \"HOMES\", 1L)"));
        assertTrue(routes.contains("metadataRepository.upsertWarpWithLimit("));
    }

    @Test
    void inMemoryLimitedResourcesAllowUpdatesButRejectNewSlots() {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        IslandLocation location = new IslandLocation("world", 0.5D, 80.0D, 0.5D, 0.0F, 0.0F);

        assertEquals("CREATED", metadata.upsertHomeWithLimit(islandId, "main", location, actorUuid, 1L));
        assertEquals("HOME_LIMIT", metadata.upsertHomeWithLimit(islandId, "second", location, actorUuid, 1L));
        assertEquals("UPDATED", metadata.upsertHomeWithLimit(islandId, "main", location, actorUuid, 1L));
        assertEquals("CREATED", metadata.upsertWarpWithLimit(islandId, "shop", location, false, actorUuid, "market", 1L));
        assertEquals("WARP_LIMIT", metadata.upsertWarpWithLimit(islandId, "second", location, false, actorUuid, "default", 1L));
        assertEquals("UPDATED", metadata.upsertWarpWithLimit(islandId, "shop", location, true, actorUuid, "market", 1L));
    }

    @Test
    void warpMutationsReportWhetherAStoredWarpChanged() throws Exception {
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();
        IslandLocation location = new IslandLocation("world", 0.5D, 80.0D, 0.5D, 0.0F, 0.0F);

        assertEquals("WARP_NOT_FOUND", metadata.setWarpPublicAccessMutationResult(islandId, "missing", true));
        assertTrue(!metadata.deleteWarpResult(islandId, "missing"));
        metadata.upsertWarp(islandId, "shop", location, false, actorUuid, "market");
        assertEquals("APPLIED", metadata.setWarpPublicAccessMutationResult(islandId, "shop", true));
        assertEquals("UNCHANGED", metadata.setWarpPublicAccessMutationResult(islandId, "shop", true));
        assertTrue(metadata.warp(islandId, "shop").orElseThrow().publicAccess());
        assertTrue(metadata.deleteWarpResult(islandId, "shop"));
        assertTrue(!metadata.deleteWarpResult(islandId, "shop"));

        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandWarpRoutes.java"));
        assertEquals(2, routes.split(java.util.regex.Pattern.quote("metadataRepository.deleteWarpResult(islandId, name)"), -1).length - 1);
        assertTrue(routes.contains("metadataRepository.setWarpPublicAccessMutationResult(islandId, name, publicAccess)"));

        String jdbc = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        int operation = jdbc.indexOf("public String setWarpPublicAccessMutationResult(");
        int nextMethod = jdbc.indexOf("\n    @Override", operation + 20);
        String transaction = jdbc.substring(operation, nextMethod);
        assertTrue(transaction.contains("SELECT public_access FROM island_warps WHERE island_id = ? AND name = ? FOR UPDATE"));
        assertTrue(transaction.contains("return \"UNCHANGED\";"));
        assertTrue(transaction.contains("return \"WARP_NOT_FOUND\";"));
        assertTrue(transaction.contains("connection.commit();"));
    }

    @Test
    void repeatedWarpAccessSkipsDuplicateLogsAndEvents() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000311");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000312");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Warp Access Test");
        metadata.upsertWarp(islandId, "shop", new IslandLocation("world", 1.0D, 65.0D, 2.0D, 90.0F, 0.0F), false, ownerUuid, "shop");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandWarpRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            logs,
            audit,
            events
        ).register(handlers::put);

        TestExchange changed = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"name\":\"shop\",\"publicAccess\":true}");
        handlers.get("/v1/islands/warps/access").handle(changed);
        TestExchange unchanged = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"name\":\"shop\",\"publicAccess\":true}");
        handlers.get("/v1/islands/warps/access").handle(unchanged);

        assertEquals(202, changed.status());
        assertTrue(changed.body().contains("\"code\":\"WARP_PUBLIC\""));
        assertEquals(200, unchanged.status());
        assertTrue(unchanged.body().contains("\"code\":\"WARP_PUBLIC\""));
        assertEquals(1, logs.list(islandId, 10).size());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_WARP_CHANGED.name()));
        assertEquals(1, audit.toJson().split("ISLAND_WARP_ACCESS_SET", -1).length - 1);
    }

    @Test
    void registersIslandWarpEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandWarpRoutes routes = new IslandWarpRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(8, paths.size());
        assertTrue(paths.contains("/v1/islands/warps"));
        assertTrue(paths.contains("/v1/islands/public-warps"));
        assertTrue(paths.contains("/v1/islands/homes"));
        assertTrue(paths.contains("/v1/islands/homes/set"));
        assertTrue(paths.contains("/v1/islands/warps/set"));
        assertTrue(paths.contains("/v1/islands/warps/delete"));
        assertTrue(paths.contains("/v1/admin/islands/warps/delete"));
        assertTrue(paths.contains("/v1/islands/warps/access"));
    }

    @Test
    void registersIslandWarpEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandWarpRoutes(null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/public-warps"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/homes"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/homes/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps/delete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/warps/delete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps/access"));
    }

    @Test
    void parsesLocationDefaultsAndOverrides() {
        assertEquals(new IslandLocation("", 0.5D, 100.0D, 0.5D, 0.0F, 0.0F), IslandWarpRoutes.location("{}"));
        assertEquals(
            new IslandLocation("island_world", 1.25D, 80.5D, -4.0D, 90.0F, 12.5F),
            IslandWarpRoutes.location("{\"worldName\":\"island_world\",\"localX\":1.25,\"localY\":80.5,\"localZ\":-4.0,\"yaw\":90.0,\"pitch\":12.5}")
        );
    }

    @Test
    void validatesHomeAndWarpNamesBeforeDatabasePersistence() {
        assertTrue(IslandWarpRoutes.validResourceName("default"));
        assertTrue(IslandWarpRoutes.validResourceName("public market"));
        assertTrue(IslandWarpRoutes.validResourceName("가게"));
        assertTrue(!IslandWarpRoutes.validResourceName(""));
        assertTrue(!IslandWarpRoutes.validResourceName(" "));
        assertTrue(!IslandWarpRoutes.validResourceName("a".repeat(33)));
        assertTrue(!IslandWarpRoutes.validResourceName("bad\nname"));
    }

    @Test
    void resourceNamesNormalizeIndependentlyOfServerLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertEquals("shop", IslandWarpRoutes.normalizeResourceName("  SHOP  "));
            assertEquals("island", IslandWarpRoutes.normalizeResourceName("ISLAND"));
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void rendersHomeAndWarpContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandLocation location = new IslandLocation("world \"one\"", 1.0D, 65.5D, -3.25D, 90.0F, 15.0F);

        Map<?, ?> homes = SimpleJson.object(SimpleJson.parse(
            IslandWarpRoutes.homesJson(List.of(new IslandHomeSnapshot(islandId, "main \"home\"", location, actorUuid, Instant.parse("2026-01-02T03:04:05Z"))))
        ));
        Map<?, ?> home = SimpleJson.object(SimpleJson.list(homes.get("homes")).get(0));
        Map<?, ?> warps = SimpleJson.object(SimpleJson.parse(
            IslandWarpRoutes.warpsJson(List.of(new IslandWarpSnapshot(islandId, "shop \"warp\"", location, true, actorUuid, Instant.parse("2026-01-02T03:04:05Z"), "Market")))
        ));
        Map<?, ?> warp = SimpleJson.object(SimpleJson.list(warps.get("warps")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(home.get("islandId")));
        assertEquals("main \"home\"", SimpleJson.text(home.get("name")));
        assertEquals("world \"one\"", SimpleJson.text(home.get("worldName")));
        assertLocation(home);
        assertEquals(actorUuid.toString(), SimpleJson.text(home.get("createdBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(home.get("createdAt")));
        assertEquals(islandId.toString(), SimpleJson.text(warp.get("islandId")));
        assertEquals("shop \"warp\"", SimpleJson.text(warp.get("name")));
        assertEquals("world \"one\"", SimpleJson.text(warp.get("worldName")));
        assertLocation(warp);
        assertEquals(true, warp.get("publicAccess"));
        assertEquals("market", SimpleJson.text(warp.get("category")));
        assertEquals(actorUuid.toString(), SimpleJson.text(warp.get("createdBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(warp.get("createdAt")));
    }

    @Test
    void normalizesWarpCategories() {
        assertEquals("default", IslandWarpSnapshot.normalizeCategory(""));
        assertEquals("public-market", IslandWarpSnapshot.normalizeCategory("Public Market"));
    }

    @Test
    void adminDeleteWarpBypassesPlayerPermissionAndEmitsOperatorAudit() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000302");
        UUID creatorUuid = UUID.fromString("00000000-0000-0000-0000-000000000303");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Admin Warp Test");
        metadata.upsertWarp(islandId, "market", new IslandLocation("world", 1.0D, 65.0D, 2.0D, 90.0F, 0.0F), true, creatorUuid, "shop");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandWarpRoutes(
            islands,
            metadata,
            new InMemoryIslandLimitRepository(),
            new InMemoryIslandPermissionRuleRepository(),
            logs,
            audit,
            events
        ).register(handlers::put);

        TestExchange accepted = exchange("{\"islandId\":\"" + islandId + "\",\"name\":\"market\"}");
        handlers.get("/v1/admin/islands/warps/delete").handle(accepted);

        assertEquals(202, accepted.status());
        assertTrue(accepted.body().contains("\"accepted\":true"));
        assertTrue(metadata.warp(islandId, "market").isEmpty());
        assertTrue(audit.toJson().contains("ISLAND_WARP_ADMIN_DELETE"));
        assertEquals("ISLAND_WARP_ADMIN_DELETE", logs.list(islandId, 10).get(0).action());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_WARP_DELETED.name()));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_WARP_CHANGED.name()));
    }

    private TestExchange exchange(String body) {
        return new TestExchange(body);
    }

    private static void assertLocation(Map<?, ?> value) {
        assertEquals(1.0D, ((Number) value.get("localX")).doubleValue());
        assertEquals(65.5D, ((Number) value.get("localY")).doubleValue());
        assertEquals(-3.25D, ((Number) value.get("localZ")).doubleValue());
        assertEquals(90.0F, ((Number) value.get("yaw")).floatValue());
        assertEquals(15.0F, ((Number) value.get("pitch")).floatValue());
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
