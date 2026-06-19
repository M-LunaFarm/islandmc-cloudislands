package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerProfileRoutesTest {
    @Test
    void registersPlayerProfileEndpointGroup() {
        List<String> paths = new ArrayList<>();
        PlayerProfileRoutes routes = new PlayerProfileRoutes(null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(5, paths.size());
        assertTrue(paths.contains("/v1/admin/players/info"));
        assertTrue(paths.contains("/v1/players/info"));
        assertTrue(paths.contains("/v1/players/touch"));
        assertTrue(paths.contains("/v1/admin/players/setisland"));
        assertTrue(paths.contains("/v1/admin/players/clearisland"));
    }
}
