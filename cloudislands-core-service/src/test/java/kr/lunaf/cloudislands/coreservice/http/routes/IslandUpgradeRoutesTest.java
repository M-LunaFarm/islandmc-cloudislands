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
import kr.lunaf.cloudislands.coreservice.audit.InMemoryAuditLogger;
import kr.lunaf.cloudislands.coreservice.bank.InMemoryIslandBankRepository;
import kr.lunaf.cloudislands.coreservice.event.InMemoryGlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.islandlog.InMemoryIslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.InMemoryIslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.InMemoryIslandRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.InMemoryIslandUpgradeRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.IslandUpgradeService;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePurchaseResult;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradeRule;
import org.junit.jupiter.api.Test;

class IslandUpgradeRoutesTest {
    @Test
    void registersIslandUpgradeEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandUpgradeRoutes routes = new IslandUpgradeRoutes(null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(3, paths.size());
        assertTrue(paths.contains("/v1/islands/upgrades"));
        assertTrue(paths.contains("/v1/islands/upgrades/purchase"));
        assertTrue(paths.contains("/v1/islands/upgrades/recalculate"));
    }

    @Test
    void registersIslandUpgradeEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandUpgradeRoutes(null, null, null, null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/upgrades"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/upgrades/purchase"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/upgrades/recalculate"));
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
        Map<?, ?> recalculation = SimpleJson.object(SimpleJson.parse(IslandUpgradeRoutes.upgradeRecalculationJson(islandId, 1, List.of(snapshot))));
        Map<?, ?> recalculatedUpgrade = SimpleJson.object(SimpleJson.list(recalculation.get("upgrades")).get(0));

        assertUpgrade(islandId, listedUpgrade);
        assertEquals(true, purchase.get("accepted"));
        assertEquals("UPGRADED", SimpleJson.text(purchase.get("code")));
        assertEquals("10.00", SimpleJson.text(purchase.get("cost")));
        assertUpgrade(islandId, purchasedUpgrade);
        assertEquals(true, recalculation.get("accepted"));
        assertEquals(1, ((Number) recalculation.get("applied")).intValue());
        assertUpgrade(islandId, recalculatedUpgrade);
    }

    @Test
    void recalculatesStoredUpgradeEffectsAgainstIslandState() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000201");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000202");
        InMemoryIslandUpgradeRepository upgrades = new InMemoryIslandUpgradeRepository();
        InMemoryIslandLimitRepository limits = new InMemoryIslandLimitRepository();
        InMemoryIslandRepository islands = new InMemoryIslandRepository();
        InMemoryIslandMetadataRepository metadata = new InMemoryIslandMetadataRepository();
        InMemoryIslandBankRepository bank = new InMemoryIslandBankRepository();
        InMemoryIslandLogRepository logs = new InMemoryIslandLogRepository();
        InMemoryGlobalEventPublisher events = new InMemoryGlobalEventPublisher();
        islands.createOwnedIsland(islandId, ownerUuid, "default", "Upgrade Recalc");
        upgrades.setLevel(islandId, "size", UpgradeType.ISLAND_SIZE, 2);
        UpgradePolicy policy = new UpgradePolicy(Map.of(
            "size", new UpgradeRule("size", UpgradeType.ISLAND_SIZE, 3, BigDecimal.ZERO, BigDecimal.ONE, Map.of(2, 175L))
        ));
        IslandUpgradeRoutes routes = new IslandUpgradeRoutes(
            upgrades,
            new IslandUpgradeService(upgrades, bank, policy),
            policy,
            bank,
            limits,
            islands,
            metadata,
            new InMemoryIslandPermissionRuleRepository(),
            logs,
            new InMemoryAuditLogger(),
            events
        );

        int applied = routes.recalculateUpgradeEffects(islandId, ownerUuid);

        assertEquals(1, applied);
        assertEquals(175L, limits.list(islandId).stream()
            .filter(limit -> limit.limitKey().equals("SIZE"))
            .findFirst()
            .orElseThrow()
            .value());
        assertEquals(175, islands.findById(islandId).orElseThrow().size());
        assertTrue(logs.list(islandId, 10).stream().anyMatch(record -> record.action().equals("ISLAND_UPGRADE_RECALCULATE")));
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
