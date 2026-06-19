package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProgressionRoutesTest {
    @Test
    void registersProgressionEndpointGroup() {
        List<String> paths = new ArrayList<>();
        ProgressionRoutes routes = new ProgressionRoutes(null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(9, paths.size());
        assertTrue(paths.contains("/v1/rankings/level"));
        assertTrue(paths.contains("/v1/rankings/worth"));
        assertTrue(paths.contains("/v1/upgrades/rules"));
        assertTrue(paths.contains("/v1/admin/block-values/list"));
        assertTrue(paths.contains("/v1/islands/missions/complete"));
        assertTrue(paths.contains("/v1/islands/limits/set"));
    }
}
