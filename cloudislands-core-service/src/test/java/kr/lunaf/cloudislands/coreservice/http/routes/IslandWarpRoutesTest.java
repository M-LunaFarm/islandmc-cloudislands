package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import org.junit.jupiter.api.Test;

class IslandWarpRoutesTest {
    @Test
    void registersIslandWarpEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandWarpRoutes routes = new IslandWarpRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/islands/warps"));
        assertTrue(paths.contains("/v1/islands/public-warps"));
        assertTrue(paths.contains("/v1/islands/homes"));
        assertTrue(paths.contains("/v1/islands/homes/set"));
        assertTrue(paths.contains("/v1/islands/warps/set"));
        assertTrue(paths.contains("/v1/islands/warps/delete"));
        assertTrue(paths.contains("/v1/islands/warps/access"));
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
    void rendersHomeAndWarpContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandLocation location = new IslandLocation("world \"one\"", 1.0D, 65.5D, -3.25D, 90.0F, 15.0F);

        assertEquals(
            "{\"homes\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"main \\\"home\\\"\",\"worldName\":\"world \\\"one\\\"\",\"localX\":1.0,\"localY\":65.5,\"localZ\":-3.25,\"yaw\":90.0,\"pitch\":15.0,\"createdBy\":\"00000000-0000-0000-0000-000000000002\",\"createdAt\":\"2026-01-02T03:04:05Z\"}]}",
            IslandWarpRoutes.homesJson(List.of(new IslandHomeSnapshot(islandId, "main \"home\"", location, actorUuid, Instant.parse("2026-01-02T03:04:05Z"))))
        );
        assertEquals(
            "{\"warps\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"shop \\\"warp\\\"\",\"localX\":1.0,\"localY\":65.5,\"localZ\":-3.25,\"yaw\":90.0,\"pitch\":15.0,\"publicAccess\":true,\"createdBy\":\"00000000-0000-0000-0000-000000000002\",\"createdAt\":\"2026-01-02T03:04:05Z\"}]}",
            IslandWarpRoutes.warpsJson(List.of(new IslandWarpSnapshot(islandId, "shop \"warp\"", location, true, actorUuid, Instant.parse("2026-01-02T03:04:05Z"))))
        );
    }
}
