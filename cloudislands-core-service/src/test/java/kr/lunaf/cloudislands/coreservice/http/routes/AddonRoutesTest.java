package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class AddonRoutesTest {
    @Test
    void registersAddonEndpointGroup() {
        List<String> paths = new ArrayList<>();
        AddonRoutes routes = new AddonRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        List<String> expectedPaths = addonEndpointPaths();
        assertEquals(expectedPaths.size(), paths.size());
        expectedPaths.forEach(path -> assertTrue(paths.contains(path), path));
    }

    @Test
    void registersAddonEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new AddonRoutes(null, null, null).register(registry);

        List<String> expectedPaths = addonEndpointPaths();
        assertEquals(expectedPaths.size(), registry.size());
        expectedPaths.forEach(path -> assertEquals(Set.of("POST"), registry.methods(path), path));
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

    private static List<String> addonEndpointPaths() {
        return List.of(
            "/v1/admin/addons/state/summary",
            "/v1/addons/state",
            "/v1/addons/state/set",
            "/v1/addons/state/bulk",
            "/v1/addons/state/save",
            AddonStateBulkSaveRequest.GLOBAL_LEGACY_ENDPOINT,
            AddonStateBulkSaveRequest.GLOBAL_ENDPOINT,
            AddonStateBulkSaveRequest.GLOBAL_BULK_SAVE_ALIAS,
            AddonStateBulkSaveRequest.GLOBAL_BULK_ALIAS,
            "/v1/addons/state/table/bulk",
            AddonStateBulkLoadRequest.GLOBAL_ENDPOINT,
            AddonStateBulkLoadRequest.GLOBAL_TABLE_LOAD_ALIAS,
            AddonStateBulkSaveRequest.GLOBAL_TABLE_BULK_SET_ENDPOINT,
            "/v1/addons/state/table/replace",
            "/v1/addons/state/table/clear",
            "/v1/addons/state/remove",
            "/v1/addons/state/clear",
            "/v1/addons/islands/state",
            "/v1/addons/islands/state/set",
            "/v1/addons/islands/state/bulk",
            "/v1/addons/islands/state/save",
            AddonStateBulkSaveRequest.ISLAND_LEGACY_ENDPOINT,
            AddonStateBulkSaveRequest.ISLAND_ENDPOINT,
            AddonStateBulkSaveRequest.ISLAND_BULK_SAVE_ALIAS,
            AddonStateBulkSaveRequest.ISLAND_BULK_ALIAS,
            AddonStateBulkSaveRequest.ISLAND_TABLE_BULK_SET_ENDPOINT,
            AddonStateBulkLoadRequest.ISLAND_ENDPOINT,
            AddonStateBulkLoadRequest.ISLAND_TABLE_LOAD_ALIAS,
            "/v1/addons/islands/state/table/bulk",
            "/v1/addons/islands/state/table/replace",
            "/v1/addons/islands/state/table/clear",
            "/v1/addons/islands/state/remove",
            "/v1/addons/islands/state/clear"
        );
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

        int size() {
            return methods.size();
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
