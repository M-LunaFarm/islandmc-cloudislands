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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import org.junit.jupiter.api.Test;

class IslandSettingsRoutesTest {
    @Test
    void jdbcRenameDistinguishesUnchangedDuplicateAndMissingOutcomes() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandRepository.java"));
        int operation = source.indexOf("public String renameResult(");
        int nextMethod = source.indexOf("\n    @Override", operation + 20);
        String transaction = source.substring(operation, nextMethod);

        assertTrue(transaction.contains("SELECT name FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE"));
        assertTrue(transaction.contains("SELECT 1 FROM islands WHERE lower(name) = lower(?)"));
        assertTrue(transaction.contains("return \"UNCHANGED\";"));
        assertTrue(transaction.contains("return \"ISLAND_NAME_TAKEN\";"));
        assertTrue(transaction.contains("return \"ISLAND_NOT_FOUND\";"));
        assertTrue(transaction.contains("uniqueViolation(exception)"), "the unique index must arbitrate concurrent cross-island renames");
    }

    @Test
    void lockAndAccessMutationsUseAffectedRowResults() throws Exception {
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandSettingsRoutes.java"));
        String metadata = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandMetadataRepository.java"));
        String islands = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/repository/JdbcIslandRepository.java"));

        assertTrue(routes.contains("metadataRepository.setLockedMutationResult(islandId, locked)"));
        assertTrue(routes.contains("islandRepository.setPublicAccessMutationResult(islandId, publicAccess)"));
        assertTrue(metadata.contains("public String setLockedMutationResult("));
        assertTrue(metadata.contains("SELECT locked FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE"));
        assertTrue(metadata.contains("UPDATE islands SET locked = ?, updated_at = now() WHERE id = ? AND deleted_at IS NULL"));
        assertTrue(metadata.contains("return \"UNCHANGED\";"));
        assertTrue(islands.contains("public String setPublicAccessMutationResult("));
        assertTrue(islands.contains("SELECT public_access FROM islands WHERE id = ? AND deleted_at IS NULL FOR UPDATE"));

        InMemoryIslandRepository repository = new InMemoryIslandRepository();
        UUID missing = UUID.randomUUID();
        assertTrue(!repository.setPublicAccessResult(missing, true));
        UUID islandId = UUID.randomUUID();
        repository.createOwnedIsland(islandId, UUID.randomUUID(), "default", "Access Result");
        assertTrue(repository.setPublicAccessResult(islandId, true));
        assertTrue(repository.findById(islandId).orElseThrow().publicAccess());
    }

    @Test
    void registersIslandSettingsEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandSettingsRoutes routes = new IslandSettingsRoutes(null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(11, paths.size());
        assertTrue(paths.contains("/v1/islands/lock"));
        assertTrue(paths.contains("/v1/islands/name"));
        assertTrue(paths.contains("/v1/admin/islands/name"));
        assertTrue(paths.contains("/v1/islands/flags"));
        assertTrue(paths.contains("/v1/islands/biome"));
        assertTrue(paths.contains("/v1/islands/biome/set"));
        assertTrue(paths.contains("/v1/admin/islands/biome/set"));
        assertTrue(paths.contains("/v1/islands/flags/set"));
        assertTrue(paths.contains("/v1/admin/islands/flags/set"));
        assertTrue(paths.contains("/v1/admin/islands/flags/reset"));
        assertTrue(paths.contains("/v1/islands/access"));
    }

    @Test
    void registersIslandSettingsEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandSettingsRoutes(null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/lock"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/name"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/name"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/flags"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/biome"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/biome/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/biome/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/flags/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/flags/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/islands/flags/reset"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/access"));
    }

    @Test
    void setAccessUpdatesBothMetadataAndAuthoritativeIslandSnapshot() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000091");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000092");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Access Test");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandSettingsRoutes(
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            new InMemoryGlobalEventPublisher()
        ).register(handlers::put);

        TestExchange exchange = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"publicAccess\":true}");
        handlers.get("/v1/islands/access").handle(exchange);

        assertEquals(202, exchange.status());
        assertTrue(metadata.isPublicAccess(islandId));
        assertTrue(islands.findById(islandId).orElseThrow().publicAccess());
    }

    @Test
    void repeatedLockAndAccessWritesAreIdempotent() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000095");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000096");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Idempotent Access");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandSettingsRoutes(islands, metadata, new InMemoryIslandPermissionRuleRepository(), logs, audit, events).register(handlers::put);

        String lockBody = "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"locked\":true}";
        String accessBody = "{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"publicAccess\":true}";
        TestExchange firstLock = exchange(lockBody);
        TestExchange secondLock = exchange(lockBody);
        TestExchange firstAccess = exchange(accessBody);
        TestExchange secondAccess = exchange(accessBody);
        handlers.get("/v1/islands/lock").handle(firstLock);
        handlers.get("/v1/islands/lock").handle(secondLock);
        handlers.get("/v1/islands/access").handle(firstAccess);
        handlers.get("/v1/islands/access").handle(secondAccess);

        assertEquals(202, firstLock.status());
        assertEquals(200, secondLock.status());
        assertTrue(secondLock.body().contains("ISLAND_LOCK_UNCHANGED"));
        assertEquals(202, firstAccess.status());
        assertEquals(200, secondAccess.status());
        assertTrue(secondAccess.body().contains("ISLAND_ACCESS_UNCHANGED"));
        assertEquals(2L, events.countByType(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name()));
        assertEquals(2, logs.list(islandId, 10).size());
        assertTrue(audit.toJson().contains("ISLAND_LOCK_SET"));
        assertTrue(audit.toJson().contains("ISLAND_ACCESS_SET"));
    }

    @Test
    void rendersSettingsContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LinkedHashMap<IslandFlag, String> flags = new LinkedHashMap<>();
        flags.put(IslandFlag.VISITOR_INTERACT, "false");
        flags.put(IslandFlag.FLY, "allow \"staff\"");

        Map<?, ?> renamed = SimpleJson.object(SimpleJson.parse(IslandSettingsRoutes.renameJson(islandId, "Sky \"Base\"")));
        Map<?, ?> renderedFlags = SimpleJson.object(SimpleJson.parse(IslandSettingsRoutes.flagsJson(new IslandFlagsSnapshot(islandId, flags))));
        Map<?, ?> renderedFlagValues = SimpleJson.object(renderedFlags.get("flags"));
        Map<?, ?> renderedBiome = SimpleJson.object(SimpleJson.parse(
            IslandSettingsRoutes.biomeJson(new IslandBiomeSnapshot(islandId, "minecraft:plains", actorUuid, Instant.parse("2026-01-02T03:04:05Z")))
        ));
        Map<?, ?> biomeSet = SimpleJson.object(SimpleJson.parse(IslandSettingsRoutes.biomeSetJson(islandId, actorUuid, "minecraft:desert")));

        assertEquals(true, renamed.get("accepted"));
        assertEquals(islandId.toString(), SimpleJson.text(renamed.get("islandId")));
        assertEquals("Sky \"Base\"", SimpleJson.text(renamed.get("name")));
        assertEquals(islandId.toString(), SimpleJson.text(renderedFlags.get("islandId")));
        assertEquals("false", SimpleJson.text(renderedFlagValues.get("VISITOR_INTERACT")));
        assertEquals("allow \"staff\"", SimpleJson.text(renderedFlagValues.get("FLY")));
        assertEquals(islandId.toString(), SimpleJson.text(renderedBiome.get("islandId")));
        assertEquals("minecraft:plains", SimpleJson.text(renderedBiome.get("biomeKey")));
        assertEquals(actorUuid.toString(), SimpleJson.text(renderedBiome.get("updatedBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(renderedBiome.get("updatedAt")));
        assertEquals(true, biomeSet.get("accepted"));
        assertEquals("minecraft:desert", SimpleJson.text(biomeSet.get("biomeKey")));
        assertEquals(actorUuid.toString(), SimpleJson.text(biomeSet.get("updatedBy")));
    }

    @Test
    void setBiomeNormalizesSupportedKeysAndRejectsUnsupportedKeys() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000102");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Biome Test");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandSettingsRoutes(
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            new InMemoryIslandLogRepository(),
            new InMemoryAuditLogger(),
            events
        ).register(handlers::put);

        TestExchange accepted = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"biomeKey\":\"desert\"}");
        handlers.get("/v1/islands/biome/set").handle(accepted);
        TestExchange rejected = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"biomeKey\":\"minecraft:the_void\"}");
        handlers.get("/v1/islands/biome/set").handle(rejected);

        assertEquals(202, accepted.status());
        assertTrue(accepted.body().contains("\"accepted\":true"));
        assertTrue(accepted.body().contains("\"biomeKey\":\"minecraft:desert\""));
        assertEquals("minecraft:desert", metadata.biome(islandId).biomeKey());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_BIOME_CHANGED.name()));
        assertEquals(400, rejected.status());
        assertTrue(rejected.body().contains("\"code\":\"INVALID_BIOME_KEY\""));
        assertEquals("minecraft:desert", metadata.biome(islandId).biomeKey());
    }

    @Test
    void setBiomeSkipsDuplicateWritesLogsAndEvents() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000103");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000104");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Biome Duplicate Test");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandSettingsRoutes(
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            logs,
            audit,
            events
        ).register(handlers::put);

        TestExchange unchanged = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\",\"biomeKey\":\"plains\"}");
        handlers.get("/v1/islands/biome/set").handle(unchanged);

        assertEquals(202, unchanged.status());
        assertTrue(unchanged.body().contains("\"code\":\"BIOME_UNCHANGED\""));
        assertTrue(unchanged.body().contains("\"biomeKey\":\"minecraft:plains\""));
        assertEquals("minecraft:plains", metadata.biome(islandId).biomeKey());
        assertEquals(0L, events.countByType(CloudIslandEventType.ISLAND_BIOME_CHANGED.name()));
        assertTrue(logs.list(islandId, 10).isEmpty());
        assertTrue(!audit.toJson().contains("ISLAND_BIOME_SET"));
    }

    @Test
    void adminRenameBypassesPlayerPermissionAndEmitsOperatorAudit() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000202");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Admin Rename Test");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandSettingsRoutes(
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            logs,
            audit,
            events
        ).register(handlers::put);

        TestExchange accepted = exchange("{\"islandId\":\"" + islandId + "\",\"name\":\"Renamed By Admin\"}");
        handlers.get("/v1/admin/islands/name").handle(accepted);
        TestExchange unchanged = exchange("{\"islandId\":\"" + islandId + "\",\"name\":\"Renamed By Admin\"}");
        handlers.get("/v1/admin/islands/name").handle(unchanged);

        assertEquals(202, accepted.status());
        assertTrue(accepted.body().contains("\"accepted\":true"));
        assertTrue(accepted.body().contains("\"name\":\"Renamed By Admin\""));
        assertEquals("Renamed By Admin", islands.findById(islandId).orElseThrow().name());
        assertTrue(audit.toJson().contains("ISLAND_ADMIN_RENAME"));
        assertEquals("ISLAND_ADMIN_RENAME", logs.list(islandId, 10).get(0).action());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_RENAMED.name()));
        assertEquals(200, unchanged.status());
        assertTrue(unchanged.body().contains("ISLAND_NAME_UNCHANGED"));
        assertEquals(1, logs.list(islandId, 10).size());

        UUID otherIslandId = UUID.fromString("00000000-0000-0000-0000-000000000205");
        islands.createOwnedIsland(otherIslandId, UUID.fromString("00000000-0000-0000-0000-000000000206"), "default", "Other Island");
        assertEquals("ISLAND_NAME_TAKEN", islands.renameResult(otherIslandId, "renamed by admin"));
        assertEquals("ISLAND_NOT_FOUND", islands.renameResult(UUID.randomUUID(), "Missing Island"));
    }

    @Test
    void adminSetBiomeNormalizesKeyAndEmitsOperatorAudit() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000203");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000204");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Admin Biome Test");
        Map<String, HttpHandler> handlers = new HashMap<>();
        new IslandSettingsRoutes(
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            logs,
            audit,
            events
        ).register(handlers::put);

        TestExchange accepted = exchange("{\"islandId\":\"" + islandId + "\",\"biomeKey\":\"desert\"}");
        handlers.get("/v1/admin/islands/biome/set").handle(accepted);

        assertEquals(202, accepted.status());
        assertTrue(accepted.body().contains("\"code\":\"BIOME_SET\""));
        assertTrue(accepted.body().contains("\"biomeKey\":\"minecraft:desert\""));
        assertEquals("minecraft:desert", metadata.biome(islandId).biomeKey());
        assertTrue(audit.toJson().contains("ISLAND_BIOME_ADMIN_SET"));
        assertEquals("ISLAND_BIOME_ADMIN_SET", logs.list(islandId, 10).get(0).action());
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_BIOME_CHANGED.name()));
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
