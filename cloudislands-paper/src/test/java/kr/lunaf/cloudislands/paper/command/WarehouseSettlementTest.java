package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseSettlementTest {
    @Test
    void roundTripsDurableRecoveryMarker() {
        WarehouseSettlement expected = new WarehouseSettlement(
            UUID.randomUUID(),
            "stone",
            128L,
            true,
            UUID.randomUUID().toString()
        );

        WarehouseSettlement decoded = WarehouseSettlement.decode(expected.encode()).orElseThrow();

        assertEquals(expected, decoded);
        assertEquals("STONE", decoded.materialKey());
        assertEquals(WarehouseSettlement.Phase.ESCROWED, decoded.phase());
    }

    @Test
    void upgradesVersionOneMarkersWithDeterministicSettlementIdentity() {
        UUID islandId = UUID.randomUUID();
        String key = UUID.randomUUID().toString();
        String legacy = "{\"version\":1,\"islandId\":\"" + islandId + "\",\"materialKey\":\"STONE\",\"amount\":64,\"deposit\":true,\"idempotencyKey\":\"" + key + "\"}";

        WarehouseSettlement first = WarehouseSettlement.decode(legacy).orElseThrow();
        WarehouseSettlement second = WarehouseSettlement.decode(legacy).orElseThrow();

        assertEquals(first.settlementId(), second.settlementId());
        assertEquals(WarehouseSettlement.Phase.ESCROWED, first.phase());
    }

    @Test
    void rejectsCorruptOrUnsupportedMarkersWithoutGuessing() {
        assertTrue(WarehouseSettlement.decode("").isEmpty());
        assertTrue(WarehouseSettlement.decode("not-json").isEmpty());
        assertTrue(WarehouseSettlement.decode("{\"version\":2}").isEmpty());
        assertTrue(WarehouseSettlement.decode("{\"version\":1,\"deposit\":\"true\"}").isEmpty());
    }

    @Test
    void rejectsUnsafeIdempotencyKeysAndAmounts() {
        UUID islandId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new WarehouseSettlement(islandId, "STONE", 0L, true, "safe-key"));
        assertThrows(IllegalArgumentException.class, () -> new WarehouseSettlement(islandId, "STONE", 1L, true, "unsafe key"));
    }
}
