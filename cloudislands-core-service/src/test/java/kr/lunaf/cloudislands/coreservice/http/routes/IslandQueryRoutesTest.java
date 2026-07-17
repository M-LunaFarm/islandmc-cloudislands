package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.permission.InMemoryIslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.InMemoryPlayerProfileRepository;
import org.junit.jupiter.api.Test;

class IslandQueryRoutesTest {
    @Test
    void registersQueryPrefixesWithExplicitMethods() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandQueryRoutes(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null).register(registry);

        assertEquals(Set.of("GET", "DELETE"), registry.methods("/v1/islands/"));
        assertEquals(Set.of("GET"), registry.methods("/v1/players/"));
    }

    @Test
    void permissionQueryIncludesPlayerNameForOverrides() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002");
        InMemoryIslandPermissionRuleRepository permissions = new InMemoryIslandPermissionRuleRepository();
        InMemoryPlayerProfileRepository profiles = new InMemoryPlayerProfileRepository();
        permissions.putPlayerOverride(islandId, playerUuid, IslandPermission.BREAK, false);
        profiles.touch(playerUuid, "BuilderPlayer");
        IslandQueryRoutes routes = new IslandQueryRoutes(
            null, null, null, null, null, permissions, null, null,
            null, null, null, null, null, profiles, null, null
        );

        String response = routes.permissionsJson(islandId);

        assertTrue(response.contains("\"playerUuid\":\"" + playerUuid + "\""));
        assertTrue(response.contains("\"playerName\":\"BuilderPlayer\""));
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
