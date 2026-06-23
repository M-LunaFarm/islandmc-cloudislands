package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class IslandCatalogRoutesTest {
    @Test
    void registersIslandCatalogEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandCatalogRoutes routes = new IslandCatalogRoutes(null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(3, paths.size());
        assertTrue(paths.contains("/v1/islands/info"));
        assertTrue(paths.contains("/v1/islands/public"));
        assertTrue(paths.contains("/v1/islands"));
    }

    @Test
    void registersIslandCatalogEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandCatalogRoutes(null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/info"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/public"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands"));
    }

    @Test
    void rendersIslandContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID ownerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandSnapshot island = new IslandSnapshot(
            islandId,
            ownerUuid,
            "Sky \"Base\"",
            IslandState.ACTIVE,
            100,
            7L,
            "12.5",
            true,
            Instant.parse("2026-01-02T03:04:05Z"),
            Instant.parse("2026-01-03T03:04:05Z")
        );

        Map<?, ?> renderedIsland = SimpleJson.object(SimpleJson.parse(IslandCatalogRoutes.islandJson(island)));
        Map<?, ?> islands = SimpleJson.object(SimpleJson.parse(IslandCatalogRoutes.islandsJson(List.of(island))));
        Map<?, ?> listedIsland = SimpleJson.object(SimpleJson.list(islands.get("islands")).get(0));
        Map<?, ?> created = SimpleJson.object(SimpleJson.parse(
            IslandCatalogRoutes.createResultJson(new CreateIslandResult(true, "ACCEPTED", island, null))
        ));

        assertIsland(islandId, ownerUuid, renderedIsland);
        assertIsland(islandId, ownerUuid, listedIsland);
        assertEquals(true, created.get("accepted"));
        assertEquals("ACCEPTED", SimpleJson.text(created.get("code")));
        assertEquals(islandId.toString(), SimpleJson.text(created.get("islandId")));
        assertEquals(null, created.get("ticket"));
    }

    private static void assertIsland(UUID islandId, UUID ownerUuid, Map<?, ?> island) {
        assertEquals(islandId.toString(), SimpleJson.text(island.get("islandId")));
        assertEquals(ownerUuid.toString(), SimpleJson.text(island.get("ownerUuid")));
        assertEquals("Sky \"Base\"", SimpleJson.text(island.get("name")));
        assertEquals("ACTIVE", SimpleJson.text(island.get("state")));
        assertEquals(100, ((Number) island.get("size")).intValue());
        assertEquals(100, ((Number) island.get("border")).intValue());
        assertEquals(7L, ((Number) island.get("level")).longValue());
        assertEquals("12.5", SimpleJson.text(island.get("worth")));
        assertEquals(true, island.get("publicAccess"));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(island.get("createdAt")));
        assertEquals("2026-01-03T03:04:05Z", SimpleJson.text(island.get("updatedAt")));
    }

    private static final class RecordingRegistry implements CoreRouteRegistry {
        private final Map<String, Set<String>> methods = new HashMap<>();

        @Override
        public void route(String path, HttpHandler handler) {
            methods.put(path, Set.of("GET", "POST"));
        }

        @Override
        public void routeMethods(String path, HttpHandler handler, String... routeMethods) {
            LinkedHashSet<String> allowed = new LinkedHashSet<>();
            for (String method : routeMethods) {
                allowed.add(method);
            }
            methods.put(path, Set.copyOf(allowed));
        }

        Set<String> methods(String path) {
            return methods.getOrDefault(path, Set.of());
        }
    }
}
