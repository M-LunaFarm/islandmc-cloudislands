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
    void registersIslandSettingsEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandSettingsRoutes routes = new IslandSettingsRoutes(null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/islands/lock"));
        assertTrue(paths.contains("/v1/islands/name"));
        assertTrue(paths.contains("/v1/islands/flags"));
        assertTrue(paths.contains("/v1/islands/biome"));
        assertTrue(paths.contains("/v1/islands/biome/set"));
        assertTrue(paths.contains("/v1/islands/flags/set"));
        assertTrue(paths.contains("/v1/islands/access"));
    }

    @Test
    void registersIslandSettingsEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandSettingsRoutes(null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/lock"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/name"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/flags"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/biome"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/biome/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/flags/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/access"));
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
