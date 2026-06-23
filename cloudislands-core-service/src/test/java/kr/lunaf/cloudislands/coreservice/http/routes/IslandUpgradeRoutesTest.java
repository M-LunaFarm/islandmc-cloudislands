package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
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
    void registersIslandUpgradeEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandUpgradeRoutes(null, null, null, null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/upgrades"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/upgrades/purchase"));
    }

    @Test
    void rendersUpgradeContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IslandUpgradeSnapshot snapshot = new IslandUpgradeSnapshot(islandId, "size", UpgradeType.ISLAND_SIZE, 2, Instant.parse("2026-01-02T03:04:05Z"));

        Map<?, ?> upgrades = SimpleJson.object(SimpleJson.parse(IslandUpgradeRoutes.upgradesJson(List.of(snapshot))));
        Map<?, ?> listedUpgrade = SimpleJson.object(SimpleJson.list(upgrades.get("upgrades")).get(0));
        Map<?, ?> purchase = SimpleJson.object(SimpleJson.parse(
            IslandUpgradeRoutes.upgradePurchaseJson(new UpgradePurchaseResult(true, "UPGRADED", new BigDecimal("10.00"), snapshot))
        ));
        Map<?, ?> purchasedUpgrade = SimpleJson.object(purchase.get("upgrade"));

        assertUpgrade(islandId, listedUpgrade);
        assertEquals(true, purchase.get("accepted"));
        assertEquals("UPGRADED", SimpleJson.text(purchase.get("code")));
        assertEquals("10.00", SimpleJson.text(purchase.get("cost")));
        assertUpgrade(islandId, purchasedUpgrade);
    }

    private static void assertUpgrade(UUID islandId, Map<?, ?> upgrade) {
        assertEquals(islandId.toString(), SimpleJson.text(upgrade.get("islandId")));
        assertEquals("size", SimpleJson.text(upgrade.get("upgradeKey")));
        assertEquals("ISLAND_SIZE", SimpleJson.text(upgrade.get("type")));
        assertEquals(2, ((Number) upgrade.get("level")).intValue());
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(upgrade.get("updatedAt")));
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
