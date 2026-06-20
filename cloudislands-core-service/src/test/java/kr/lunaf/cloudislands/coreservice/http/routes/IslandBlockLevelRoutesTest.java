package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    void parsesCountsAndRendersLevelContract() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(2L, IslandBlockLevelRoutes.parseCountsPayload("minecraft:stone=2,bad=x").get("minecraft:stone"));
        assertEquals(0L, IslandBlockLevelRoutes.parseCountsPayload("minecraft:stone=2,bad=x").get("bad"));
        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"level\":7,\"worth\":\"12.50\",\"calculatedAt\":\"2026-01-02T03:04:05Z\"}",
            IslandBlockLevelRoutes.levelJson(new IslandRankSnapshot(islandId, 7L, new BigDecimal("12.50"), 2, Instant.parse("2026-01-02T03:04:05Z")))
        );
    }

    @Test
    void rendersBlockDetailContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Map<String, RankingRecalculationService.BlockValue> values = Map.of(
            "minecraft:diamond_block", new RankingRecalculationService.BlockValue(new BigDecimal("1000.00"), 10L, 5000L)
        );

        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"blocks\":[{\"materialKey\":\"minecraft:diamond_block\",\"count\":2,\"unitWorth\":\"1000.00\",\"totalWorth\":\"2000.00\",\"levelPoints\":20,\"limit\":5000}],\"summary\":{\"totalWorth\":\"2000.00\",\"totalLevelPoints\":20}}",
            IslandBlockLevelRoutes.blockDetailsJson(islandId, Map.of("minecraft:diamond_block", 2L), values, 10)
        );
        assertEquals(
            "{\"values\":[{\"materialKey\":\"minecraft:diamond_block\",\"worth\":\"1000.00\",\"levelPoints\":10,\"limit\":5000}]}",
            IslandBlockLevelRoutes.blockValuesJson(values)
        );
    }
}
