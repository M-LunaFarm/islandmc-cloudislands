package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpPrincipal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpException;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryIslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.InMemoryRankingRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import org.junit.jupiter.api.Test;

class IslandBlockLevelRoutesTest {
    @Test
    void registersIslandBlockAndLevelEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandBlockLevelRoutes routes = new IslandBlockLevelRoutes(null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/admin/block-values"));
        assertTrue(paths.contains("/v1/admin/block-values/list"));
        assertTrue(paths.contains("/v1/islands/blocks"));
        assertTrue(paths.contains("/v1/islands/blocks/counts"));
        assertTrue(paths.contains("/v1/islands/blocks/delta"));
        assertTrue(paths.contains("/v1/islands/blocks/replace"));
        assertTrue(paths.contains("/v1/islands/level/recalculate"));
    }

    @Test
    void registersIslandBlockAndLevelEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandBlockLevelRoutes(null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/admin/block-values"));
        assertEquals(Set.of("POST"), registry.methods("/v1/admin/block-values/list"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/blocks"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/blocks/counts"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/blocks/delta"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/blocks/replace"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/level/recalculate"));
    }

    @Test
    void parsesCountsAndRendersLevelContract() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(2L, IslandBlockLevelRoutes.parseCountsPayload("minecraft:stone=2").get("minecraft:stone"));
        assertEquals(4L, IslandBlockLevelRoutes.parseCountsBody("{\"counts\":{\"minecraft:diamond_block\":4}}").get("minecraft:diamond_block"));
        assertEquals(2L, IslandBlockLevelRoutes.parseCountsBody("{\"counts\":\"minecraft:stone=2\"}").get("minecraft:stone"));
        Map<?, ?> level = SimpleJson.object(SimpleJson.parse(
            IslandBlockLevelRoutes.levelJson(new IslandRankSnapshot(islandId, 7L, new BigDecimal("12.50"), 2, Instant.parse("2026-01-02T03:04:05Z")))
        ));

        assertEquals(islandId.toString(), SimpleJson.text(level.get("islandId")));
        assertEquals(7L, ((Number) level.get("level")).longValue());
        assertEquals("12.50", SimpleJson.text(level.get("worth")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(level.get("calculatedAt")));
    }

    @Test
    void rejectsInvalidCountNumbersInsteadOfCoercingToZero() {
        CoreHttpException mapException = assertThrows(CoreHttpException.class, () ->
            IslandBlockLevelRoutes.parseCountsBody("{\"counts\":{\"minecraft:diamond_block\":\"x\"}}")
        );
        CoreHttpException decimalException = assertThrows(CoreHttpException.class, () ->
            IslandBlockLevelRoutes.parseCountsBody("{\"counts\":{\"minecraft:diamond_block\":1.5}}")
        );
        CoreHttpException payloadException = assertThrows(CoreHttpException.class, () ->
            IslandBlockLevelRoutes.parseCountsPayload("minecraft:stone=x")
        );

        assertEquals(400, mapException.status());
        assertEquals("INVALID_REQUEST", mapException.code());
        assertEquals(400, decimalException.status());
        assertEquals("INVALID_REQUEST", decimalException.code());
        assertEquals(400, payloadException.status());
        assertEquals("INVALID_REQUEST", payloadException.code());
    }

    @Test
    void rendersBlockDetailContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<String, RankingRecalculationService.BlockValue> values = Map.of(
            "minecraft:diamond_block", new RankingRecalculationService.BlockValue(new BigDecimal("1000.00"), 10L, 5000L)
        );

        Map<?, ?> details = SimpleJson.object(SimpleJson.parse(
            IslandBlockLevelRoutes.blockDetailsJson(islandId, Map.of("minecraft:diamond_block", 2L), values, 10)
        ));
        Map<?, ?> counts = SimpleJson.object(SimpleJson.parse(
            IslandBlockLevelRoutes.blockCountsJson(islandId, Map.of("minecraft:stone", 3L, "ignored", 0L))
        ));
        Map<?, ?> block = SimpleJson.object(SimpleJson.list(details.get("blocks")).get(0));
        Map<?, ?> summary = SimpleJson.object(details.get("summary"));
        Map<?, ?> blockValues = SimpleJson.object(SimpleJson.parse(IslandBlockLevelRoutes.blockValuesJson(values)));
        Map<?, ?> value = SimpleJson.object(SimpleJson.list(blockValues.get("values")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(details.get("islandId")));
        assertEquals(3L, ((Number) SimpleJson.object(counts.get("counts")).get("minecraft:stone")).longValue());
        assertTrue(!SimpleJson.object(counts.get("counts")).containsKey("ignored"));
        assertEquals("minecraft:diamond_block", SimpleJson.text(block.get("materialKey")));
        assertEquals(2L, ((Number) block.get("count")).longValue());
        assertEquals("1000.00", SimpleJson.text(block.get("unitWorth")));
        assertEquals("2000.00", SimpleJson.text(block.get("totalWorth")));
        assertEquals(20L, ((Number) block.get("levelPoints")).longValue());
        assertEquals(5000L, ((Number) block.get("limit")).longValue());
        assertEquals("2000.00", SimpleJson.text(summary.get("totalWorth")));
        assertEquals(20L, ((Number) summary.get("totalLevelPoints")).longValue());
        assertEquals("minecraft:diamond_block", SimpleJson.text(value.get("materialKey")));
        assertEquals("1000.00", SimpleJson.text(value.get("worth")));
        assertEquals(10L, ((Number) value.get("levelPoints")).longValue());
        assertEquals(5000L, ((Number) value.get("limit")).longValue());
    }

    @Test
    void levelRecalculationWritesAuditAndEventSignals() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000302");
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandLevelRepository levels = new InMemoryIslandLevelRepository();
        InMemoryRankingRepository rankings = new InMemoryRankingRepository();
        InMemoryAuditLogger audit = new InMemoryAuditLogger();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "ranked");
        islands.setState(islandId, IslandState.INACTIVE_READY);
        metadata.upsertMemberKey(islandId, ownerUuid, "OWNER");
        levels.putBlockValue("minecraft:diamond_block", new RankingRecalculationService.BlockValue(new BigDecimal("10.00"), 5L, 0L));
        levels.replaceBlockCounts(islandId, Map.of("minecraft:diamond_block", 4L));
        IslandBlockLevelRoutes routes = new IslandBlockLevelRoutes(
            levels,
            rankings,
            new RankingRecalculationService(rankings, events, "floor(total_level_points / 10)", "SUM_BLOCK_VALUES"),
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            audit,
            events
        );
        Map<String, HttpHandler> handlers = new HashMap<>();
        routes.register(handlers::put);

        TestExchange exchange = exchange("{\"islandId\":\"" + islandId + "\",\"actorUuid\":\"" + ownerUuid + "\"}");
        handlers.get("/v1/islands/level/recalculate").handle(exchange);

        assertEquals(202, exchange.status());
        assertTrue(exchange.body().contains("\"level\":2"));
        assertTrue(audit.toJson().contains("ISLAND_LEVEL_RECALCULATE"));
        assertTrue(audit.toJson().contains("\"worth\":\"40.00\""));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_LEVEL_UPDATED.name()));
        assertEquals(1L, events.countByType(CloudIslandEventType.ISLAND_WORTH_CHANGED.name()));
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
