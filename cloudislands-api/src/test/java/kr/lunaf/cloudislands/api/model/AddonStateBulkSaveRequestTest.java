package kr.lunaf.cloudislands.api.model;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AddonStateBulkSaveRequestTest {
    @Test
    void keepsSlashSeparatedTableKeysForLegacyAndNodeMoveState() {
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.globalTables(
                "cloudislands-satis",
                Map.of("machines", Map.of("island/0001/machine/0002", "{\"status\":\"active\"}")));

        assertEquals(Map.of("island/0001/machine/0002", "{\"status\":\"active\"}"),
                request.tables().get("machines"));
        assertEquals(1, request.tableKeyCount());
        assertEquals(1, request.tableCount());
    }

    @Test
    void scopedTableRequestPreservesSlashSeparatedIslandKeys() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000701");
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.islandTable(
                "cloudislands-satis",
                islandId,
                "resource_nodes",
                Map.of("node/ore/0/0", "{\"remaining\":12000}"));

        assertEquals(Map.of("node/ore/0/0", "{\"remaining\":12000}"), request.values());
        assertEquals(Map.of("node/ore/0/0", "{\"remaining\":12000}"),
                request.tablesWithScopedTable().get("resource_nodes"));
        assertEquals(1, request.tableKeyCount());
    }
}
