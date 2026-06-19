package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
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
}
