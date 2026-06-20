package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandWarehouseItemSnapshot;
import kr.lunaf.cloudislands.coreservice.warehouse.IslandWarehouseRepository;
import org.junit.jupiter.api.Test;

class IslandWarehouseRoutesTest {
    @Test
    void registersIslandWarehouseEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandWarehouseRoutes routes = new IslandWarehouseRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(3, paths.size());
        assertTrue(paths.contains("/v1/islands/warehouse"));
        assertTrue(paths.contains("/v1/islands/warehouse/deposit"));
        assertTrue(paths.contains("/v1/islands/warehouse/withdraw"));
    }

    @Test
    void rendersWarehouseContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IslandWarehouseItemSnapshot item = new IslandWarehouseItemSnapshot(islandId, "Stone", 64L, Instant.parse("2026-01-02T03:04:05Z"));

        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"materialKey\":\"minecraft:stone\",\"amount\":64,\"updatedAt\":\"2026-01-02T03:04:05Z\"}",
            IslandWarehouseRoutes.itemJson(item)
        );
        assertEquals(
            "{\"items\":[{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"materialKey\":\"minecraft:stone\",\"amount\":64,\"updatedAt\":\"2026-01-02T03:04:05Z\"}]}",
            IslandWarehouseRoutes.warehouseJson(List.of(item))
        );
        assertEquals(
            "{\"accepted\":true,\"code\":\"DEPOSITED\",\"item\":{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"materialKey\":\"minecraft:stone\",\"amount\":64,\"updatedAt\":\"2026-01-02T03:04:05Z\"}}",
            IslandWarehouseRoutes.changeJson(new IslandWarehouseRepository.ChangeResult(true, "DEPOSITED", item))
        );
    }
}
