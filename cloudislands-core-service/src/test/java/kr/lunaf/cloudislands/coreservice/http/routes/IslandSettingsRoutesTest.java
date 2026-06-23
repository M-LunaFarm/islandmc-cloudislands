package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class IslandSettingsRoutesTest {
    @Test
    void registersIslandSettingsEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandSettingsRoutes routes = new IslandSettingsRoutes(null, null, null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(7, paths.size());
        assertTrue(paths.contains("/v1/islands/lock"));
        assertTrue(paths.contains("/v1/islands/name"));
        assertTrue(paths.contains("/v1/islands/flags"));
        assertTrue(paths.contains("/v1/islands/biome"));
        assertTrue(paths.contains("/v1/islands/biome/set"));
        assertTrue(paths.contains("/v1/islands/flags/set"));
        assertTrue(paths.contains("/v1/islands/access"));
    }

    @Test
    void registersIslandSettingsEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandSettingsRoutes(null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/lock"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/name"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/flags"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/biome"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/biome/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/flags/set"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/access"));
    }

    @Test
    void rendersSettingsContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        LinkedHashMap<IslandFlag, String> flags = new LinkedHashMap<>();
        flags.put(IslandFlag.VISITOR_INTERACT, "false");
        flags.put(IslandFlag.FLY, "allow \"staff\"");

        Map<?, ?> renamed = SimpleJson.object(SimpleJson.parse(IslandSettingsRoutes.renameJson(islandId, "Sky \"Base\"")));
        Map<?, ?> renderedFlags = SimpleJson.object(SimpleJson.parse(IslandSettingsRoutes.flagsJson(new IslandFlagsSnapshot(islandId, flags))));
        Map<?, ?> renderedFlagValues = SimpleJson.object(renderedFlags.get("flags"));
        Map<?, ?> renderedBiome = SimpleJson.object(SimpleJson.parse(
            IslandSettingsRoutes.biomeJson(new IslandBiomeSnapshot(islandId, "minecraft:plains", actorUuid, Instant.parse("2026-01-02T03:04:05Z")))
        ));

        assertEquals(true, renamed.get("accepted"));
        assertEquals(islandId.toString(), SimpleJson.text(renamed.get("islandId")));
        assertEquals("Sky \"Base\"", SimpleJson.text(renamed.get("name")));
        assertEquals(islandId.toString(), SimpleJson.text(renderedFlags.get("islandId")));
        assertEquals("false", SimpleJson.text(renderedFlagValues.get("VISITOR_INTERACT")));
        assertEquals("allow \"staff\"", SimpleJson.text(renderedFlagValues.get("FLY")));
        assertEquals(islandId.toString(), SimpleJson.text(renderedBiome.get("islandId")));
        assertEquals("minecraft:plains", SimpleJson.text(renderedBiome.get("biomeKey")));
        assertEquals(actorUuid.toString(), SimpleJson.text(renderedBiome.get("updatedBy")));
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(renderedBiome.get("updatedAt")));
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
