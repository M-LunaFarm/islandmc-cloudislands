package kr.lunaf.cloudislands.coreservice.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.common.failure.RedisOutagePolicy;
import org.junit.jupiter.api.Test;

class CachingIslandSnapshotRepositoryRedisOutageTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000502");

    @Test
    void fallsBackToDelegateWhenRedisCacheIsUnavailable() {
        InMemoryIslandSnapshotRepository delegate = new InMemoryIslandSnapshotRepository();
        CachingIslandSnapshotRepository snapshots = new CachingIslandSnapshotRepository(
            delegate,
            URI.create("redis://127.0.0.1:1")
        );

        IslandSnapshotRecord recorded = snapshots.record(
            ISLAND,
            7L,
            "islands/" + ISLAND + "/snapshots/000007/bundle.tar.zst",
            "PERIODIC",
            OWNER,
            "sha256:redis-down",
            4096L
        );

        List<IslandSnapshotRecord> listed = snapshots.list(ISLAND, 10);

        assertNotNull(recorded.snapshotId());
        assertEquals(RedisOutagePolicy.DB_DIRECT_READ_POLICY, snapshots.degradedModePolicy());
        assertEquals(1, listed.size());
        assertEquals(7L, listed.get(0).snapshotNo());
        assertEquals(recorded.snapshotId(), snapshots.find(ISLAND, 7L).orElseThrow().snapshotId());
        assertTrue(snapshots.failuresTotal() >= 2L);
    }
}
