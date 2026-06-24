package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.workflow.IslandLifecycleWorkflow;
import org.junit.jupiter.api.Test;

class IslandPlayerLifecycleRoutesTest {
    @Test
    void registersIslandPlayerLifecycleEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandPlayerLifecycleRoutes routes = new IslandPlayerLifecycleRoutes(null, null, null, null, (islandId, ownerUuid, requesterUuid, reason) -> new DeleteIslandResult(false, "NOT_OWNER_OR_MISSING", islandId), null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/islands/delete"));
        assertTrue(paths.contains("/v1/islands/reset"));
    }

    @Test
    void registersIslandPlayerLifecycleEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandPlayerLifecycleRoutes(null, null, null, null, (islandId, ownerUuid, requesterUuid, reason) -> new DeleteIslandResult(false, "NOT_OWNER_OR_MISSING", islandId), null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/delete"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/reset"));
    }

    @Test
    void rendersLifecycleContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        Map<?, ?> lifecycle = SimpleJson.object(SimpleJson.parse(
            IslandPlayerLifecycleRoutes.lifecycleJson(new IslandLifecycleWorkflow.Result(true, "RESET_ACCEPTED", null))
        ));
        Map<?, ?> deleted = SimpleJson.object(SimpleJson.parse(
            IslandPlayerLifecycleRoutes.deleteResultJson(new DeleteIslandResult(false, "NOT_OWNER_OR_MISSING", islandId))
        ));

        assertEquals(true, lifecycle.get("accepted"));
        assertEquals("RESET_ACCEPTED", SimpleJson.text(lifecycle.get("code")));
        assertEquals(false, deleted.get("accepted"));
        assertEquals("NOT_OWNER_OR_MISSING", SimpleJson.text(deleted.get("code")));
        assertEquals(islandId.toString(), SimpleJson.text(deleted.get("islandId")));
        assertEquals(202, IslandPlayerLifecycleRoutes.deleteStatus(new DeleteIslandResult(true, "DELETE_REQUESTED", islandId), 403));
        assertEquals(202, IslandPlayerLifecycleRoutes.deleteStatus(new DeleteIslandResult(true, "DELETED", islandId), 403));
        assertEquals(403, IslandPlayerLifecycleRoutes.deleteStatus(new DeleteIslandResult(false, "NOT_OWNER_OR_MISSING", islandId), 403));
        assertEquals(409, IslandPlayerLifecycleRoutes.deleteStatus(new DeleteIslandResult(false, "DELETE_QUEUE_FAILED", islandId), 403));
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
