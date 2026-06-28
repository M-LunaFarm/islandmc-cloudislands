package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
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
    void registersProgressionEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new ProgressionRoutes(null, null, null, null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/rankings/level"));
        assertEquals(Set.of("POST"), registry.methods("/v1/rankings/worth"));
        assertEquals(Set.of("POST"), registry.methods("/v1/upgrades/rules"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/missions"));
        assertEquals(Set.of("POST"), registry.methods("/v1/addons/missions/register"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/missions/complete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/missions/progress"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/limits"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/limits/set"));
    }

    @Test
    void rendersMissionAndLimitContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandMissionSnapshot mission = new IslandMissionSnapshot(
            islandId,
            "break_stone",
            "MISSION",
            "mining",
            "Break \"Stone\"",
            "Break stone blocks",
            "BLOCK_BREAK",
            "minecraft:stone",
            3L,
            10L,
            false,
            "BANK_DEPOSIT",
            "100 coins",
            false,
            false,
            Instant.parse("2026-01-02T03:04:05Z")
        );
        MissionProviderDefinitionSnapshot definition = new MissionProviderDefinitionSnapshot(
            "provider",
            "Break_Stone",
            "mission",
            "mining",
            "Break Stone",
            "Break stone blocks",
            "BLOCK_BREAK",
            "minecraft:stone",
            10L,
            "BANK_DEPOSIT",
            "100 coins",
            false,
            false,
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
        assertEquals("mining", SimpleJson.text(renderedDefinition.get("category")));
        assertEquals("Break stone blocks", SimpleJson.text(renderedDefinition.get("description")));
        assertEquals("BLOCK_BREAK", SimpleJson.text(renderedDefinition.get("triggerType")));
        assertEquals("minecraft:stone", SimpleJson.text(renderedDefinition.get("targetKey")));
        assertEquals(10L, ((Number) renderedDefinition.get("goal")).longValue());
        assertEquals("BANK_DEPOSIT", SimpleJson.text(renderedDefinition.get("rewardType")));
        assertEquals(false, renderedDefinition.get("repeatable"));
        assertEquals(false, renderedDefinition.get("dailyReset"));
        assertEquals(true, renderedDefinition.get("enabled"));
        assertLimit(islandId, actorUuid, renderedLimit);
        assertLimit(islandId, actorUuid, singleLimit);
    }

    @Test
    void appliesBankDepositMissionReward() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000091");
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();
        ProgressionRoutes routes = new ProgressionRoutes(null, null, null, null, bank, null, null, null, null, null, null, null);
        IslandMissionSnapshot mission = new IslandMissionSnapshot(
            islandId,
            "reward_bank",
            "MISSION",
            "economy",
            "Reward Bank",
            "Reward deposits into the island bank",
            "BLOCK_PLACE",
            "*",
            1L,
            1L,
            true,
            "BANK_DEPOSIT",
            "1,250.50 coins",
            false,
            false,
            Instant.parse("2026-01-02T03:04:05Z")
        );

        ProgressionRoutes.MissionRewardApplication applied = routes.applyMissionReward(mission);

        assertTrue(applied.applied());
        assertEquals("BANK_DEPOSITED", applied.code());
        assertEquals("1250.50", bank.balance(islandId).balance());
        assertEquals("1250.50", ProgressionRoutes.bankDepositRewardAmount(mission).orElseThrow().toPlainString());
        assertTrue(ProgressionRoutes.bankDepositRewardAmount(new IslandMissionSnapshot(islandId, "bad_reward", "MISSION", "Bad", 1L, 1L, true, "coins", Instant.EPOCH)).isEmpty());
    }

    private static void assertMission(UUID islandId, Map<?, ?> mission) {
        assertEquals(islandId.toString(), SimpleJson.text(mission.get("islandId")));
        assertEquals("break_stone", SimpleJson.text(mission.get("missionKey")));
        assertEquals("MISSION", SimpleJson.text(mission.get("kind")));
        assertEquals("mining", SimpleJson.text(mission.get("category")));
        assertEquals("Break \"Stone\"", SimpleJson.text(mission.get("title")));
        assertEquals("Break stone blocks", SimpleJson.text(mission.get("description")));
        assertEquals("BLOCK_BREAK", SimpleJson.text(mission.get("triggerType")));
        assertEquals("minecraft:stone", SimpleJson.text(mission.get("targetKey")));
        assertEquals(3L, ((Number) mission.get("progress")).longValue());
        assertEquals(10L, ((Number) mission.get("goal")).longValue());
        assertEquals(false, mission.get("completed"));
        assertEquals("BANK_DEPOSIT", SimpleJson.text(mission.get("rewardType")));
        assertEquals("100 coins", SimpleJson.text(mission.get("reward")));
        assertEquals(false, mission.get("repeatable"));
        assertEquals(false, mission.get("dailyReset"));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(mission.get("updatedAt")));
    }

    private static void assertLimit(UUID islandId, UUID actorUuid, Map<?, ?> limit) {
        assertEquals(islandId.toString(), SimpleJson.text(limit.get("islandId")));
        assertEquals("HOPPER", SimpleJson.text(limit.get("limitKey")));
        assertEquals(50L, ((Number) limit.get("value")).longValue());
        assertEquals(actorUuid.toString(), SimpleJson.text(limit.get("updatedBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(limit.get("updatedAt")));
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
