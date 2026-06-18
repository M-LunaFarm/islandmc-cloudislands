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
        assertEquals(Map.of("table/machines/island/0001/machine/0002", "{\"status\":\"active\"}"),
                request.flattenedStateValues());
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
        assertEquals(Map.of("table/resource_nodes/node/ore/0/0", "{\"remaining\":12000}"),
                request.flattenedStateValues());
        assertEquals(1, request.tableKeyCount());
    }

    @Test
    void rejectsNestedTableNamesButKeepsNestedKeys() {
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.globalTables(
                "cloudislands-satis",
                Map.of(
                        "machines/live", Map.of("machine/one", "ignored"),
                        "machines", Map.of("machine/one", "kept")
                ));

        assertEquals(Map.of("machine/one", "kept"), request.tables().get("machines"));
        assertEquals(1, request.tableKeyCount());
        assertEquals(1, request.tableCount());
    }

    @Test
    void dropsEmptyTablesFromBulkCounts() {
        AddonStateBulkSaveRequest request = AddonStateBulkSaveRequest.globalTables(
                "cloudislands-satis",
                Map.of(
                        "machines", Map.of(),
                        "resource_nodes", Map.of("node/ore/0/0", "12000")
                ));

        assertEquals(Map.of("node/ore/0/0", "12000"), request.tables().get("resource_nodes"));
        assertEquals(false, request.tables().containsKey("machines"));
        assertEquals(1, request.tableKeyCount());
        assertEquals(1, request.tableCount());
        assertEquals(Map.of("table/resource_nodes/node/ore/0/0", "12000"), request.flattenedStateValues());
    }

    @Test
    void scopedTableValuesMergeIntoBulkTableValues() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000702");
        AddonStateBulkSaveRequest request = new AddonStateBulkSaveRequest(
                "cloudislands-satis",
                islandId,
                "machines",
                Map.of("machine/2", "active", "machine/1", "overridden"),
                Map.of("machines", Map.of("machine/1", "idle"))
        );

        assertEquals(Map.of("machine/1", "overridden", "machine/2", "active"),
                request.tablesWithScopedTable().get("machines"));
        assertEquals(Map.of(
                "table/machines/machine/1", "overridden",
                "table/machines/machine/2", "active"
        ), request.flattenedStateValues());
        assertEquals(2, request.tableKeyCount());
        assertEquals(1, request.tableCount());
    }

    @Test
    void exposesShortTableKeyValueBulkSaveAliasesAndContractShape() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000703");

        AddonStateBulkSaveRequest global = AddonStateBulkSaveRequest.tableKeyValueBulkSave(
                "cloudislands-satis",
                Map.of("runtime-status", "ok"),
                Map.of("machines", Map.of("machine/1", "active")));
        AddonStateBulkSaveRequest globalTable = AddonStateBulkSaveRequest.tableKeyValueBulkSave(
                "cloudislands-satis",
                "machines",
                Map.of("machine/2", "idle"));
        AddonStateBulkSaveRequest island = AddonStateBulkSaveRequest.tableKeyValueBulkSave(
                "cloudislands-satis",
                islandId,
                Map.of("active-node", "island-2"),
                Map.of("resource_nodes", Map.of("node/ore/0/0", "12000")));
        AddonStateBulkSaveRequest islandTable = AddonStateBulkSaveRequest.tableKeyValueBulkSave(
                "cloudislands-satis",
                islandId,
                "resource_nodes",
                Map.of("node/oil/1/0", "8000"));

        assertEquals("table-key-value-bulk-save", AddonStateBulkSaveRequest.API_NAME);
        assertEquals("addonId,islandId?,values?,tables.{table}.{key}=value", AddonStateBulkSaveRequest.WIRE_SHAPE);
        assertEquals("table-key-value-bulk-save", global.apiName());
        assertEquals("global", global.scopeName());
        assertEquals("island", island.scopeName());
        assertEquals(Map.of("machine/1", "active"), global.tables().get("machines"));
        assertEquals(Map.of("machine/2", "idle"), globalTable.tablesWithScopedTable().get("machines"));
        assertEquals(Map.of("node/ore/0/0", "12000"), island.tables().get("resource_nodes"));
        assertEquals(Map.of("node/oil/1/0", "8000"), islandTable.tablesWithScopedTable().get("resource_nodes"));
    }
}
