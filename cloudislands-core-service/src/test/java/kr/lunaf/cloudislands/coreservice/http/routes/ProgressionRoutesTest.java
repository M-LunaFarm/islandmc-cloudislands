package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class ProgressionRoutesTest {
    @Test
    void registersProgressionEndpointGroup() {
        List<String> paths = new ArrayList<>();
        ProgressionRoutes routes = new ProgressionRoutes(null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(9, paths.size());
        assertTrue(paths.contains("/v1/rankings/level"));
        assertTrue(paths.contains("/v1/rankings/worth"));
        assertTrue(paths.contains("/v1/upgrades/rules"));
        assertTrue(paths.contains("/v1/addons/missions/register"));
        assertTrue(paths.contains("/v1/islands/missions/complete"));
        assertTrue(paths.contains("/v1/islands/limits/set"));
    }

    @Test
    void rendersMissionAndLimitContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandMissionSnapshot mission = new IslandMissionSnapshot(
            islandId,
            "break_stone",
            "MISSION",
            "Break \"Stone\"",
            3L,
            10L,
            false,
            "100 coins",
            Instant.parse("2026-01-02T03:04:05Z")
        );
        MissionProviderDefinitionSnapshot definition = new MissionProviderDefinitionSnapshot(
            "provider",
            "Break_Stone",
            "mission",
            "Break Stone",
            10L,
            "100 coins",
            true,
            Instant.parse("2026-01-02T03:04:05Z")
        );
        IslandLimitSnapshot limit = new IslandLimitSnapshot(islandId, "HOPPER", 50L, actorUuid, Instant.parse("2026-01-02T03:04:05Z"));

        Map<?, ?> missionRoot = SimpleJson.object(SimpleJson.parse(ProgressionRoutes.missionsJson(List.of(mission))));
        Map<?, ?> renderedMission = SimpleJson.object(SimpleJson.list(missionRoot.get("missions")).get(0));
        Map<?, ?> singleMission = SimpleJson.object(SimpleJson.parse(ProgressionRoutes.missionJson(mission)));
        Map<?, ?> definitions = SimpleJson.object(SimpleJson.parse(ProgressionRoutes.missionDefinitionsJson(List.of(definition))));
        Map<?, ?> renderedDefinition = SimpleJson.object(SimpleJson.list(definitions.get("missions")).get(0));
        Map<?, ?> limits = SimpleJson.object(SimpleJson.parse(ProgressionRoutes.limitsJson(List.of(limit))));
        Map<?, ?> renderedLimit = SimpleJson.object(SimpleJson.list(limits.get("limits")).get(0));
        Map<?, ?> singleLimit = SimpleJson.object(SimpleJson.parse(ProgressionRoutes.limitJson(limit)));

        assertMission(islandId, renderedMission);
        assertMission(islandId, singleMission);
        assertEquals("provider", SimpleJson.text(renderedDefinition.get("providerId")));
        assertEquals("break_stone", SimpleJson.text(renderedDefinition.get("missionKey")));
        assertEquals("MISSION", SimpleJson.text(renderedDefinition.get("kind")));
        assertEquals(10L, ((Number) renderedDefinition.get("goal")).longValue());
        assertEquals(true, renderedDefinition.get("enabled"));
        assertLimit(islandId, actorUuid, renderedLimit);
        assertLimit(islandId, actorUuid, singleLimit);
    }

    private static void assertMission(UUID islandId, Map<?, ?> mission) {
        assertEquals(islandId.toString(), SimpleJson.text(mission.get("islandId")));
        assertEquals("break_stone", SimpleJson.text(mission.get("missionKey")));
        assertEquals("MISSION", SimpleJson.text(mission.get("kind")));
        assertEquals("Break \"Stone\"", SimpleJson.text(mission.get("title")));
        assertEquals(3L, ((Number) mission.get("progress")).longValue());
        assertEquals(10L, ((Number) mission.get("goal")).longValue());
        assertEquals(false, mission.get("completed"));
        assertEquals("100 coins", SimpleJson.text(mission.get("reward")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(mission.get("updatedAt")));
    }

    private static void assertLimit(UUID islandId, UUID actorUuid, Map<?, ?> limit) {
        assertEquals(islandId.toString(), SimpleJson.text(limit.get("islandId")));
        assertEquals("HOPPER", SimpleJson.text(limit.get("limitKey")));
        assertEquals(50L, ((Number) limit.get("value")).longValue());
        assertEquals(actorUuid.toString(), SimpleJson.text(limit.get("updatedBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(limit.get("updatedAt")));
    }
}
