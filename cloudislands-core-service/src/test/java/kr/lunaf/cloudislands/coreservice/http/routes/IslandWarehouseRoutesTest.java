package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;
import org.junit.jupiter.api.Test;

class IslandWarehouseRoutesTest {
    @Test
    void warehouseMutationsUseContainerPermissionInsteadOfBankWithdrawalPermission() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/IslandWarehouseRoutes.java"));

        assertEquals(3, occurrences(source, "IslandPermission.OPEN_CONTAINER"));
        assertFalse(source.contains("IslandPermission.WITHDRAW_BANK"));
    }

    @Test
    void registersIslandWarehouseEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandWarehouseRoutes routes = new IslandWarehouseRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/islands/warehouse"));
        assertTrue(paths.contains("/v1/islands/warehouse/deposit"));
        assertTrue(paths.contains("/v1/islands/warehouse/withdraw"));
        assertTrue(paths.contains("/v1/players/warehouse-settlement/find"));
        assertTrue(paths.contains("/v1/players/warehouse-settlement/prepare"));
        assertTrue(paths.contains("/v1/players/warehouse-settlement/escrow"));
        assertTrue(paths.contains("/v1/players/warehouse-settlement/clear"));
    }

    @Test
    void registersIslandWarehouseEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandWarehouseRoutes(null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warehouse"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warehouse/deposit"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warehouse/withdraw"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/warehouse-settlement/find"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/warehouse-settlement/prepare"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/warehouse-settlement/escrow"));
        assertEquals(Set.of("POST"), registry.methods("/v1/players/warehouse-settlement/clear"));
    }

    @Test
    void rendersWarehouseContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IslandWarehouseItemSnapshot item = new IslandWarehouseItemSnapshot(islandId, "Stone", 64L, Instant.parse("2026-01-02T03:04:05Z"));

        Map<?, ?> renderedItem = SimpleJson.object(SimpleJson.parse(IslandWarehouseRoutes.itemJson(item)));
        Map<?, ?> warehouse = SimpleJson.object(SimpleJson.parse(IslandWarehouseRoutes.warehouseJson(List.of(item))));
        Map<?, ?> listedItem = SimpleJson.object(SimpleJson.list(warehouse.get("items")).get(0));
        Map<?, ?> change = SimpleJson.object(SimpleJson.parse(
            IslandWarehouseRoutes.changeJson(new IslandWarehouseRepository.ChangeResult(true, "DEPOSITED", item))
        ));
        Map<?, ?> changedItem = SimpleJson.object(change.get("item"));

        assertItem(islandId, renderedItem);
        assertItem(islandId, listedItem);
        assertEquals(true, change.get("accepted"));
        assertEquals("DEPOSITED", SimpleJson.text(change.get("code")));
        assertItem(islandId, changedItem);
    }

    private static void assertItem(UUID islandId, Map<?, ?> item) {
        assertEquals(islandId.toString(), SimpleJson.text(item.get("islandId")));
        assertEquals("minecraft:stone", SimpleJson.text(item.get("materialKey")));
        assertEquals(64L, ((Number) item.get("amount")).longValue());
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(item.get("updatedAt")));
    }

    private static int occurrences(String source, String needle) {
        return (source.length() - source.replace(needle, "").length()) / needle.length();
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
