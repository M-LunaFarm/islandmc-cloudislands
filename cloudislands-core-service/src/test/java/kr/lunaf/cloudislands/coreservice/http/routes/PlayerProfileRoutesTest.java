package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import org.junit.jupiter.api.Test;

class PlayerProfileRoutesTest {
    @Test
    void registersPlayerProfileEndpointGroup() {
        List<String> paths = new ArrayList<>();
        PlayerProfileRoutes routes = new PlayerProfileRoutes(null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(6, paths.size());
        assertTrue(paths.contains("/v1/admin/players/info"));
        assertTrue(paths.contains("/v1/players/info"));
        assertTrue(paths.contains("/v1/players/touch"));
        assertTrue(paths.contains("/v1/players/locale"));
        assertTrue(paths.contains("/v1/admin/players/setisland"));
        assertTrue(paths.contains("/v1/admin/players/clearisland"));
    }

    @Test
    void playerProfileJsonIncludesLocale() {
        String json = PlayerProfileRoutes.playerProfileJson(new kr.lunaf.cloudislands.api.model.PlayerIslandProfile(
            java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "Steve, \"Builder\"",
            java.util.Optional.empty(),
            java.time.Instant.EPOCH,
            "EN-US"
        ));
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(json));

        assertEquals("Steve, \"Builder\"", SimpleJson.text(root.get("lastName")));
        assertNull(root.get("primaryIslandId"));
        assertEquals("en_us", SimpleJson.text(root.get("locale")));
    }
}
