package kr.lunaf.cloudislands.paper.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

class FallbackIslandStorageTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000042");
    private static final byte[] PAYLOAD = "portable-island-bundle".getBytes(StandardCharsets.UTF_8);

    @Test
    void productionFallbackStorageDoesNotBufferBundlesWithReadAllBytes() throws IOException {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/storage/FallbackIslandStorage.java"));

        assertFalse(source.contains("readAllBytes("));
    }

    @Test
    void writesPrimaryAndMirrorsFallbackFromSpool() throws IOException {
        RecordingStorage primary = new RecordingStorage(false);
        RecordingStorage fallback = new RecordingStorage(false);
        FallbackIslandStorage storage = new FallbackIslandStorage(primary, fallback, Logger.getLogger("test"));

        storage.writeSnapshot(ISLAND_ID, 7L, new ByteArrayInputStream(PAYLOAD), manifest());

        assertArrayEquals(PAYLOAD, primary.snapshotPayload);
        assertArrayEquals(PAYLOAD, fallback.snapshotPayload);
        assertEquals(0L, storage.fallbackWrites());
    }

    @Test
    void writesFallbackFromSpoolWhenPrimaryFails() throws IOException {
        RecordingStorage primary = new RecordingStorage(true);
        RecordingStorage fallback = new RecordingStorage(false);
        FallbackIslandStorage storage = new FallbackIslandStorage(primary, fallback, Logger.getLogger("test"));

        storage.writeDeleteBackup(ISLAND_ID, 8L, new ByteArrayInputStream(PAYLOAD), manifest());

        assertArrayEquals(PAYLOAD, fallback.deleteBackupPayload);
        assertEquals(1L, storage.fallbackWrites());
    }

    private static IslandBundleManifest manifest() {
        return new IslandBundleManifest(
            ISLAND_ID,
            UUID.fromString("00000000-0000-0000-0000-000000000007"),
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            "1.21.11",
            1,
            100,
            new IslandLocation("world", 0, 64, 0, 0, 0),
            Instant.EPOCH,
            Instant.EPOCH,
            "checksum"
        );
    }

    private static final class RecordingStorage implements IslandStorage {
        private final boolean failWrites;
        private byte[] snapshotPayload = new byte[0];
        private byte[] deleteBackupPayload = new byte[0];

        private RecordingStorage(boolean failWrites) {
            this.failWrites = failWrites;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public IslandBundleManifest readManifest(UUID islandId) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public InputStream openLatestBundle(UUID islandId) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public InputStream openBundle(String storagePath) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
            if (failWrites) {
                throw new IOException("primary unavailable");
            }
            snapshotPayload = bundle.readAllBytes();
            return new StoredBundle("checksum", snapshotPayload.length);
        }

        @Override
        public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
            if (failWrites) {
                throw new IOException("primary unavailable");
            }
            deleteBackupPayload = bundle.readAllBytes();
            return new StoredBundle("checksum", deleteBackupPayload.length);
        }

        @Override
        public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public void promoteSnapshot(UUID islandId, long snapshotNo) {
        }

        @Override
        public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) {
        }

        @Override
        public int pruneSnapshots(UUID islandId, int keepLatest) {
            return 0;
        }

        @Override
        public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) {
            return 0;
        }

        @Override
        public void deleteLiveState(UUID islandId) {
        }

        @Override
        public void deleteIsland(UUID islandId) {
        }
    }
}
