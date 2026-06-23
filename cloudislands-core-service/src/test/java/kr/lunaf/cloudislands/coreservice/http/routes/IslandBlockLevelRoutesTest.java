package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpException;
import kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import org.junit.jupiter.api.Test;

class IslandBlockLevelRoutesTest {
    @Test
    void registersIslandBlockAndLevelEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandBlockLevelRoutes routes = new IslandBlockLevelRoutes(null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/admin/block-values"));
        assertTrue(paths.contains("/v1/admin/block-values/list"));
        assertTrue(paths.contains("/v1/islands/blocks"));
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
        Map<?, ?> block = SimpleJson.object(SimpleJson.list(details.get("blocks")).get(0));
        Map<?, ?> summary = SimpleJson.object(details.get("summary"));
        Map<?, ?> blockValues = SimpleJson.object(SimpleJson.parse(IslandBlockLevelRoutes.blockValuesJson(values)));
        Map<?, ?> value = SimpleJson.object(SimpleJson.list(blockValues.get("values")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(details.get("islandId")));
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
