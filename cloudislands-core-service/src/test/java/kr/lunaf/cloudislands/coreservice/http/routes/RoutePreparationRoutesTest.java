package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoutePreparationRoutesTest {
    @Test
    void registersRoutePreparationEndpointGroup() {
        List<String> paths = new ArrayList<>();
        RoutePreparationRoutes routes = new RoutePreparationRoutes(null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/routes/home"));
        assertTrue(paths.contains("/v1/routes/migration-return"));
        assertTrue(paths.contains("/v1/admin/islands/tp"));
    }
}
