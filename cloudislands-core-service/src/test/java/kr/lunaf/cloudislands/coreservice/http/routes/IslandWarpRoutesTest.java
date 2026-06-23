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
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class IslandWarpRoutesTest {
    @Test
    void registersIslandWarpEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandWarpRoutes routes = new IslandWarpRoutes(null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/islands/warps"));
        assertTrue(paths.contains("/v1/islands/public-warps"));
        assertTrue(paths.contains("/v1/islands/homes"));
        assertTrue(paths.contains("/v1/islands/homes/set"));
        assertTrue(paths.contains("/v1/islands/warps/set"));
        assertTrue(paths.contains("/v1/islands/warps/delete"));
        assertTrue(paths.contains("/v1/islands/warps/access"));
    }

    @Test
    void registersIslandWarpEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandWarpRoutes(null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/public-warps"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/homes"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/homes/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps/delete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/warps/access"));
    }

    @Test
    void parsesLocationDefaultsAndOverrides() {
        assertEquals(new IslandLocation("", 0.5D, 100.0D, 0.5D, 0.0F, 0.0F), IslandWarpRoutes.location("{}"));
        assertEquals(
            new IslandLocation("island_world", 1.25D, 80.5D, -4.0D, 90.0F, 12.5F),
            IslandWarpRoutes.location("{\"worldName\":\"island_world\",\"localX\":1.25,\"localY\":80.5,\"localZ\":-4.0,\"yaw\":90.0,\"pitch\":12.5}")
        );
    }

    @Test
    void rendersHomeAndWarpContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        IslandLocation location = new IslandLocation("world \"one\"", 1.0D, 65.5D, -3.25D, 90.0F, 15.0F);

        Map<?, ?> homes = SimpleJson.object(SimpleJson.parse(
            IslandWarpRoutes.homesJson(List.of(new IslandHomeSnapshot(islandId, "main \"home\"", location, actorUuid, Instant.parse("2026-01-02T03:04:05Z"))))
        ));
        Map<?, ?> home = SimpleJson.object(SimpleJson.list(homes.get("homes")).get(0));
        Map<?, ?> warps = SimpleJson.object(SimpleJson.parse(
            IslandWarpRoutes.warpsJson(List.of(new IslandWarpSnapshot(islandId, "shop \"warp\"", location, true, actorUuid, Instant.parse("2026-01-02T03:04:05Z"), "Market")))
        ));
        Map<?, ?> warp = SimpleJson.object(SimpleJson.list(warps.get("warps")).get(0));

        assertEquals(islandId.toString(), SimpleJson.text(home.get("islandId")));
        assertEquals("main \"home\"", SimpleJson.text(home.get("name")));
        assertEquals("world \"one\"", SimpleJson.text(home.get("worldName")));
        assertLocation(home);
        assertEquals(actorUuid.toString(), SimpleJson.text(home.get("createdBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(home.get("createdAt")));
        assertEquals(islandId.toString(), SimpleJson.text(warp.get("islandId")));
        assertEquals("shop \"warp\"", SimpleJson.text(warp.get("name")));
        assertLocation(warp);
        assertEquals(true, warp.get("publicAccess"));
        assertEquals("market", SimpleJson.text(warp.get("category")));
        assertEquals(actorUuid.toString(), SimpleJson.text(warp.get("createdBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(warp.get("createdAt")));
    }

    @Test
    void normalizesWarpCategories() {
        assertEquals("default", IslandWarpSnapshot.normalizeCategory(""));
        assertEquals("public-market", IslandWarpSnapshot.normalizeCategory("Public Market"));
    }

    private static void assertLocation(Map<?, ?> value) {
        assertEquals(1.0D, ((Number) value.get("localX")).doubleValue());
        assertEquals(65.5D, ((Number) value.get("localY")).doubleValue());
        assertEquals(-3.25D, ((Number) value.get("localZ")).doubleValue());
        assertEquals(90.0F, ((Number) value.get("yaw")).floatValue());
        assertEquals(15.0F, ((Number) value.get("pitch")).floatValue());
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
