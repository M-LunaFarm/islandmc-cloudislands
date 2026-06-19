package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditRoutesTest {
    @Test
    void registersAuditEndpointGroup() {
        List<String> paths = new ArrayList<>();
        AuditRoutes routes = new AuditRoutes(null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(3, paths.size());
        assertTrue(paths.contains("/v1/audit"));
        assertTrue(paths.contains("/v1/admin/audit"));
        assertTrue(paths.contains("/v1/admin/audit/list"));
    }
}
