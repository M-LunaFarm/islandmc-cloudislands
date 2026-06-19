package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SuperiorSkyblock2MigrationRoutesTest {
    @Test
    void registersMigrationEndpointGroup() {
        List<String> paths = new ArrayList<>();
        SuperiorSkyblock2MigrationRoutes routes = new SuperiorSkyblock2MigrationRoutes(false, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/admin/migrations/superiorskyblock2/scan"));
        assertTrue(paths.contains("/v1/admin/migrations/superiorskyblock2/status"));
        assertTrue(paths.contains("/v1/admin/migrations/superiorskyblock2/rollback"));
    }

    @Test
    void rendersDisabledMigrationResponse() {
        String json = SuperiorSkyblock2MigrationRoutes.disabledJson();

        assertTrue(json.contains("\"code\":\"MIGRATION_DISABLED\""));
        assertTrue(json.contains("\"state\":\"DISABLED\""));
        assertTrue(json.contains("\"sourcePlugin\":\"SuperiorSkyblock2\""));
    }
}
