package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class AdminNodeRoutesTest {
    @Test
    void registersAdminNodeEndpointGroup() {
        List<String> routes = new ArrayList<>();
        List<String> prefixes = new ArrayList<>();
        AdminNodeRoutes adminNodes = new AdminNodeRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> adminNodes.register((path, handler) -> routes.add(path), (path, handler) -> prefixes.add(path)));

        assertEquals(5, routes.size());
        assertTrue(routes.contains("/v1/admin/nodes/drain"));
        assertTrue(routes.contains("/v1/admin/nodes/sweep"));
        assertEquals(List.of("/v1/admin/nodes/"), prefixes);
    }

    @Test
    void rendersNodeLifecyclePolicy() {
        String json = AdminNodeRoutes.nodeLifecycleJson("node-1", "DRAINING", "DRAIN");

        assertTrue(json.contains("\"accepted\":true"));
        assertTrue(json.contains("\"nodeId\":\"node-1\""));
        assertTrue(json.contains("\"operation\":\"DRAIN\""));
        assertEquals("DRAIN", AdminNodeRoutes.nodeLifecycleFields("node-1", "DRAINING", "DRAIN").get("operation"));
    }

    @Test
    void rendersNodeSweepNodeList() {
        List<?> nodes = SimpleJson.list(SimpleJson.parse(AdminNodeRoutes.nodesJson(List.of("node-1", "node-\"2"))));

        assertEquals(List.of("node-1", "node-\"2"), nodes);
    }
}
