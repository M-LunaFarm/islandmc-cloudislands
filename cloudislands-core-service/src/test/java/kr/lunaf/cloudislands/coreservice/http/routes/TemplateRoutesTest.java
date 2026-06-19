package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.coreservice.template.IslandTemplateSnapshot;
import org.junit.jupiter.api.Test;

class TemplateRoutesTest {
    @Test
    void registersTemplateEndpointGroup() {
        List<String> paths = new ArrayList<>();
        TemplateRoutes routes = new TemplateRoutes(null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(4, paths.size());
        assertTrue(paths.contains("/v1/admin/templates/list"));
        assertTrue(paths.contains("/v1/admin/templates/upsert"));
        assertTrue(paths.contains("/v1/admin/templates/enable"));
        assertTrue(paths.contains("/v1/admin/templates/disable"));
    }

    @Test
    void rendersTemplateList() {
        String json = TemplateRoutes.templatesJson(List.of(
            new IslandTemplateSnapshot("default", "Default Island", true, ""),
            new IslandTemplateSnapshot("sky", "Sky \"Island\"", false, "1.2")
        ));

        assertTrue(json.contains("\"id\":\"default\""));
        assertTrue(json.contains("\"enabled\":true"));
        assertTrue(json.contains("\"displayName\":\"Sky \\\"Island\\\"\""));
        assertTrue(json.contains("\"minNodeVersion\":\"1.2\""));
    }

    @Test
    void protectsMigrationInputOnlyTemplate() {
        assertTrue(TemplateRoutes.migrationInputOnlyTemplate("superiorskyblock2"));
        assertTrue(TemplateRoutes.migrationInputOnlyTemplate(" SuperiorSkyblock2 "));
    }
}
