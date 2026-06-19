package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePurchaseResult;
import org.junit.jupiter.api.Test;

class IslandUpgradeRoutesTest {
    @Test
    void registersIslandUpgradeEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandUpgradeRoutes routes = new IslandUpgradeRoutes(null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/islands/upgrades"));
        assertTrue(paths.contains("/v1/islands/upgrades/purchase"));
    }

    @Test
    void rendersUpgradeContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IslandUpgradeSnapshot snapshot = new IslandUpgradeSnapshot(islandId, "size", UpgradeType.ISLAND_SIZE, 2, Instant.parse("2026-01-02T03:04:05Z"));

        assertEquals(
            "{\"upgrades\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"upgradeKey\":\"size\",\"type\":\"ISLAND_SIZE\",\"level\":2,\"updatedAt\":\"2026-01-02T03:04:05Z\"}]}",
            IslandUpgradeRoutes.upgradesJson(List.of(snapshot))
        );
        assertEquals(
            "{\"accepted\":true,\"code\":\"UPGRADED\",\"cost\":\"10.00\",\"upgrade\":{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"upgradeKey\":\"size\",\"type\":\"ISLAND_SIZE\",\"level\":2,\"updatedAt\":\"2026-01-02T03:04:05Z\"}}",
            IslandUpgradeRoutes.upgradePurchaseJson(new UpgradePurchaseResult(true, "UPGRADED", new BigDecimal("10.00"), snapshot))
        );
    }
}
