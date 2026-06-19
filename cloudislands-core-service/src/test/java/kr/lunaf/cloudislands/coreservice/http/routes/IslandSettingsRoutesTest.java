package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
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
    void rendersSettingsContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LinkedHashMap<IslandFlag, String> flags = new LinkedHashMap<>();
        flags.put(IslandFlag.VISITOR_INTERACT, "false");
        flags.put(IslandFlag.FLY, "allow \"staff\"");

        assertEquals(
            "{\"accepted\":true,\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Sky \\\"Base\\\"\"}",
            IslandSettingsRoutes.renameJson(islandId, "Sky \"Base\"")
        );
        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"flags\":{\"VISITOR_INTERACT\":\"false\",\"FLY\":\"allow \\\"staff\\\"\"}}",
            IslandSettingsRoutes.flagsJson(new IslandFlagsSnapshot(islandId, flags))
        );
        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"biomeKey\":\"minecraft:plains\",\"updatedBy\":\"00000000-0000-0000-0000-000000000002\",\"updatedAt\":\"2026-01-02T03:04:05Z\"}",
            IslandSettingsRoutes.biomeJson(new IslandBiomeSnapshot(islandId, "minecraft:plains", actorUuid, Instant.parse("2026-01-02T03:04:05Z")))
        );
    }
}
