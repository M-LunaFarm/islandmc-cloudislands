package kr.lunaf.cloudislands.api.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AddonStateBulkLoadRequestTest {
    @Test
    void exposesTableKeyValueBulkLoadEndpointsAndAliases() {
        assertEquals("table-key-value-bulk-load", AddonStateBulkLoadRequest.API_NAME);
        assertEquals("addonId,islandId?,table", AddonStateBulkLoadRequest.WIRE_SHAPE);
        assertEquals("/v1/addons/state/table/key-value/bulk-load", AddonStateBulkLoadRequest.GLOBAL_ENDPOINT);
        assertEquals("/v1/addons/islands/state/table/key-value/bulk-load", AddonStateBulkLoadRequest.ISLAND_ENDPOINT);
        assertEquals(
            List.of(
                "/v1/addons/state/table/key-value/bulk-load",
                "/v1/addons/state/table/load"
            ),
            AddonStateBulkLoadRequest.GLOBAL_ENDPOINTS
        );
        assertEquals(
            List.of(
                "/v1/addons/islands/state/table/key-value/bulk-load",
                "/v1/addons/islands/state/table/load"
            ),
            AddonStateBulkLoadRequest.ISLAND_ENDPOINTS
        );
    }

    @Test
    void normalizesTableNamesForGlobalAndIslandLoads() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000711");

        AddonStateBulkLoadRequest global = AddonStateBulkLoadRequest.tableKeyValueBulkLoad(
            " cloudislands-satis ",
            "table/machines/"
        );
        AddonStateBulkLoadRequest island = AddonStateBulkLoadRequest.tableKeyValueBulkLoad(
            "cloudislands-satis",
            islandId,
            "/resource_nodes/"
        );

        assertEquals("cloudislands-satis", global.addonId());
        assertEquals("machines", global.table());
        assertEquals("global", global.scopeName());
        assertEquals("table-key-value-bulk-load", global.apiName());
        assertFalse(global.islandScoped());
        assertEquals("resource_nodes", island.table());
        assertEquals("island", island.scopeName());
        assertTrue(island.islandScoped());
    }

    @Test
    void rejectsNestedTableNamesByBlankingUnsafeTable() {
        AddonStateBulkLoadRequest request = AddonStateBulkLoadRequest.global(
            "cloudislands-satis",
            "machines/live"
        );

        assertEquals("", request.table());
    }
}
