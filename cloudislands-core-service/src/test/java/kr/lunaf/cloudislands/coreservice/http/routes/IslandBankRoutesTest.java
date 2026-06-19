package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import org.junit.jupiter.api.Test;

class IslandBankRoutesTest {
    @Test
    void registersIslandBankEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandBankRoutes routes = new IslandBankRoutes(null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(3, paths.size());
        assertTrue(paths.contains("/v1/islands/bank"));
        assertTrue(paths.contains("/v1/islands/bank/deposit"));
        assertTrue(paths.contains("/v1/islands/bank/withdraw"));
    }

    @Test
    void rendersBankContractAndParsesAmount() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertEquals(new BigDecimal("12.50"), IslandBankRoutes.amount("{\"amount\":\"12.50\"}"));
        assertEquals(BigDecimal.ZERO, IslandBankRoutes.amount("{\"amount\":\"bad\"}"));
        assertEquals(
            "{\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"balance\":\"100.25\",\"updatedAt\":\"2026-01-02T03:04:05Z\"}",
            IslandBankRoutes.bankJson(new IslandBankSnapshot(islandId, "100.25", Instant.parse("2026-01-02T03:04:05Z")))
        );
    }
}
