package kr.lunaf.cloudislands.coreservice.http.routes;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
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
    void rendersSnapshotContracts() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID snapshotId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals("islands/00000000-0000-0000-0000-000000000001/snapshots/000007/bundle.tar.zst", IslandSnapshotRoutes.defaultStoragePath(islandId, 7L));
        assertEquals("3", IslandSnapshotRoutes.snapshotEventFields(islandId, 7L, "path", "AUTO", "abc", 12L, "node-1", 9L, 3).get("pruned"));
        assertEquals(
            "{\"snapshots\":[{\"snapshotId\":\"00000000-0000-0000-0000-000000000002\",\"islandId\":\"00000000-0000-0000-0000-000000000001\",\"snapshotNo\":7,\"storagePath\":\"path\",\"reason\":\"AUTO\",\"createdBy\":\"null\",\"checksum\":\"abc\",\"sizeBytes\":12,\"createdAt\":\"2026-01-02T03:04:05Z\"}]}",
            IslandSnapshotRoutes.snapshotsJson(List.of(new IslandSnapshotRecord(snapshotId, islandId, 7L, "path", "AUTO", null, "abc", 12L, Instant.parse("2026-01-02T03:04:05Z"))))
        );
    }
}
