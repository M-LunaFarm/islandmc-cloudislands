package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;
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

        Map<?, ?> cleared = SimpleJson.object(SimpleJson.parse(result.json(false)));
        Map<?, ?> reloaded = SimpleJson.object(SimpleJson.parse(result.json(true)));

        assertEquals(2, ((Number) cleared.get("clearedSessions")).intValue());
        assertEquals(3, ((Number) cleared.get("clearedTickets")).intValue());
        assertEquals(4, ((Number) cleared.get("clearedRedisKeys")).intValue());
        assertEquals(true, reloaded.get("reloaded"));
        assertEquals(2, ((Number) reloaded.get("clearedSessions")).intValue());
        assertEquals(3, ((Number) reloaded.get("clearedTickets")).intValue());
        assertEquals(4, ((Number) reloaded.get("clearedRedisKeys")).intValue());
        assertEquals("application-cache", result.cacheClearEventFields().get("scope"));
        assertEquals("2", result.reloadEventFields().get("clearedSessions"));
    }
}
