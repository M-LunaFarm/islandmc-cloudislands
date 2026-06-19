package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdminRuntimeRoutesTest {
    @Test
    void registersAdminRuntimeEndpointGroup() {
        List<String> paths = new ArrayList<>();
        AdminRuntimeRoutes routes = new AdminRuntimeRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/admin/cache/clear"));
        assertTrue(paths.contains("/v1/admin/reload"));
    }

    @Test
    void rendersCacheClearAndReloadResponses() {
        AdminRuntimeRoutes.ClearResult result = new AdminRuntimeRoutes.ClearResult(2, 3, 4);

        assertEquals("{\"clearedSessions\":2,\"clearedTickets\":3,\"clearedRedisKeys\":4}", result.json(false));
        assertEquals("{\"reloaded\":true,\"clearedSessions\":2,\"clearedTickets\":3,\"clearedRedisKeys\":4}", result.json(true));
        assertEquals("application-cache", result.cacheClearEventFields().get("scope"));
        assertEquals("2", result.reloadEventFields().get("clearedSessions"));
    }
}
