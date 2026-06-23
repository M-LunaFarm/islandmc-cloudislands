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
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import org.junit.jupiter.api.Test;

class IslandSnapshotRoutesTest {
    @Test
    void registersIslandSnapshotEndpointGroup() {
        List<String> paths = new ArrayList<>();
        IslandSnapshotRoutes routes = new IslandSnapshotRoutes(null, null, null, null);

        assertDoesNotThrow(() -> routes.register((path, handler) -> paths.add(path)));

        assertEquals(2, paths.size());
        assertTrue(paths.contains("/v1/islands/snapshots"));
        assertTrue(paths.contains("/v1/islands/snapshots/record"));
    }

    @Test
    void registersIslandSnapshotEndpointsAsPostOnly() {
        RecordingRegistry registry = new RecordingRegistry();

        new IslandSnapshotRoutes(null, null, null, null).register(registry);

        assertEquals(Set.of("POST"), registry.methods("/v1/islands/snapshots"));
        assertEquals(Set.of("POST"), registry.methods("/v1/islands/snapshots/record"));
    }

    @Test
    void rendersSnapshotContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals("islands/00000000-0000-0000-0000-000000000001/snapshots/000007/bundle.tar.zst", IslandSnapshotRoutes.defaultStoragePath(islandId, 7L));
        assertEquals("3", IslandSnapshotRoutes.snapshotEventFields(islandId, 7L, "path", "AUTO", "abc", 12L, "node-1", 9L, 3).get("pruned"));
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(
            IslandSnapshotRoutes.snapshotsJson(List.of(new IslandSnapshotRecord(snapshotId, islandId, 7L, "path", "AUTO", null, "abc", 12L, Instant.parse("2026-01-02T03:04:05Z"))))
        ));
        Map<?, ?> snapshot = SimpleJson.object(SimpleJson.list(root.get("snapshots")).get(0));
        Map<?, ?> accepted = SimpleJson.object(SimpleJson.parse(
            IslandSnapshotRoutes.recordAcceptedJson(7L, "path", "abc", 12L, 9L, 3)
        ));

        assertEquals(snapshotId.toString(), SimpleJson.text(snapshot.get("snapshotId")));
        assertEquals(islandId.toString(), SimpleJson.text(snapshot.get("islandId")));
        assertEquals(7L, ((Number) snapshot.get("snapshotNo")).longValue());
        assertEquals("path", SimpleJson.text(snapshot.get("storagePath")));
        assertEquals("AUTO", SimpleJson.text(snapshot.get("reason")));
        assertEquals("null", SimpleJson.text(snapshot.get("createdBy")));
        assertEquals("abc", SimpleJson.text(snapshot.get("checksum")));
        assertEquals(12L, ((Number) snapshot.get("sizeBytes")).longValue());
        assertEquals("2026-01-02T03:04:05Z", SimpleJson.text(snapshot.get("createdAt")));
        assertEquals(true, accepted.get("accepted"));
        assertEquals(7L, ((Number) accepted.get("snapshotNo")).longValue());
        assertEquals("path", SimpleJson.text(accepted.get("storagePath")));
        assertEquals(3, ((Number) accepted.get("pruned")).intValue());
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
