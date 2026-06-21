package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class AddonRoutesTest {
    @Test
    void registersAddonEndpointGroup() {
        List<String> paths = new ArrayList<>();
        AddonRoutes routes = new AddonRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(33, paths.size());
        assertTrue(paths.contains("/v1/admin/addons/state/summary"));
        assertTrue(paths.contains("/v1/addons/state"));
        assertTrue(paths.contains(AddonStateBulkSaveRequest.GLOBAL_ENDPOINT));
        assertTrue(paths.contains(AddonStateBulkLoadRequest.GLOBAL_ENDPOINT));
        assertTrue(paths.contains("/v1/addons/state/table/replace"));
        assertTrue(paths.contains("/v1/addons/islands/state"));
        assertTrue(paths.contains(AddonStateBulkSaveRequest.ISLAND_ENDPOINT));
        assertTrue(paths.contains(AddonStateBulkLoadRequest.ISLAND_ENDPOINT));
        assertTrue(paths.contains("/v1/addons/islands/state/table/clear"));
    }

    @Test
    void rendersAddonStateContracts() {
        Map<String, String> state = new LinkedHashMap<>();
        state.put("plain", "value");
        state.put("quote\"key", "line\nvalue");
        state.put(null, "skipped");
        state.put("null-value", null);

        Map<?, ?> stateRoot = SimpleJson.object(SimpleJson.parse(AddonRoutes.addonStateJson(state)));
        Map<?, ?> values = SimpleJson.object(stateRoot.get("values"));

        assertEquals("value", SimpleJson.text(values.get("plain")));
        assertEquals("line\nvalue", SimpleJson.text(values.get("quote\"key")));
        assertEquals(false, values.containsKey("null-value"));

        Map<?, ?> summary = SimpleJson.object(SimpleJson.parse(AddonRoutes.addonStateSummaryJson(
            Map.of("addon-b", 2, "addon-a", 1),
            Map.of("addon-b", 3)
        )));
        List<?> addons = SimpleJson.list(summary.get("addons"));
        Map<?, ?> first = SimpleJson.object(addons.get(0));
        Map<?, ?> second = SimpleJson.object(addons.get(1));

        assertEquals("core-addon-state-store", SimpleJson.text(summary.get("stateOwnership")));
        assertEquals(false, summary.get("registeredAddonRequired"));
        assertEquals("addon-a", SimpleJson.text(first.get("addonId")));
        assertEquals(1L, ((Number) first.get("globalKeys")).longValue());
        assertEquals(0L, ((Number) first.get("islandKeys")).longValue());
        assertEquals(1L, ((Number) first.get("totalKeys")).longValue());
        assertEquals("addon-b", SimpleJson.text(second.get("addonId")));
        assertEquals(2L, ((Number) second.get("globalKeys")).longValue());
        assertEquals(3L, ((Number) second.get("islandKeys")).longValue());
        assertEquals(5L, ((Number) second.get("totalKeys")).longValue());
    }
}
