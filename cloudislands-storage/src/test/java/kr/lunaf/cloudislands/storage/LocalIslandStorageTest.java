package kr.lunaf.cloudislands.storage;

import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalIslandStorageTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final Instant CREATED_AT = Instant.parse("2026-06-17T00:00:00Z");
    private static final Instant SAVED_AT = Instant.parse("2026-06-17T01:00:00Z");

    @TempDir
    Path root;

    @Test
    void writeSnapshotStoresPortableLatestManifestBundleAndChecksum() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        byte[] bundle = "portable-bundle".getBytes(StandardCharsets.UTF_8);

        IslandStorage.StoredBundle stored = storage.writeSnapshot(ISLAND_ID, 7L, input(bundle), manifest("AUTO_HOURLY"));

        assertEquals(checksum(bundle), stored.checksum());
        assertEquals(bundle.length, stored.sizeBytes());
        assertEquals("islands/" + ISLAND_ID + "/snapshots/000007/bundle.tar.zst", stored.storagePath());
        assertEquals("SHA-256", stored.checksumAlgorithm());
        assertEquals("zstd", stored.compression());

        IslandBundleManifest latest = storage.readManifest(ISLAND_ID);
        assertEquals(stored.checksum(), latest.checksum());
        assertEquals(stored.storagePath(), latest.storagePath());
        assertTrue(latest.portable());
        assertEquals("AUTO_HOURLY", latest.snapshotReason());
        assertEquals("portable-bundle", new String(storage.openLatestBundle(ISLAND_ID).readAllBytes(), StandardCharsets.UTF_8));

        Optional<IslandBundleManifest> directManifest = storage.readBundleManifest(stored.storagePath());
        assertTrue(directManifest.isPresent());
        assertEquals(stored.checksum(), directManifest.orElseThrow().checksum());

        Path checksums = root.resolve("islands").resolve(ISLAND_ID.toString()).resolve("snapshots").resolve("000007").resolve("checksums.sha256");
        assertEquals(stored.checksum() + "  bundle.tar.zst\n", Files.readString(checksums, StandardCharsets.UTF_8));
    }

    @Test
    void deleteBackupDoesNotReplaceLatestLiveSnapshot() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        byte[] liveBundle = "live-state".getBytes(StandardCharsets.UTF_8);
        byte[] backupBundle = "delete-backup".getBytes(StandardCharsets.UTF_8);

        IslandStorage.StoredBundle live = storage.writeSnapshot(ISLAND_ID, 1L, input(liveBundle), manifest("AUTO_HOURLY"));
        IslandStorage.StoredBundle backup = storage.writeDeleteBackup(ISLAND_ID, 2L, input(backupBundle), manifest("BEFORE_DELETE"));

        IslandBundleManifest latest = storage.readManifest(ISLAND_ID);
        assertEquals(live.storagePath(), latest.storagePath());
        assertEquals(live.checksum(), latest.checksum());
        assertEquals("live-state", new String(storage.openLatestBundle(ISLAND_ID).readAllBytes(), StandardCharsets.UTF_8));

        IslandBundleManifest backupManifest = storage.readBundleManifest(backup.storagePath()).orElseThrow();
        assertEquals(backup.checksum(), backupManifest.checksum());
        assertEquals("BEFORE_DELETE", backupManifest.snapshotReason());
        assertEquals("islands/" + ISLAND_ID + "/backups/delete-000002/bundle.tar.zst", backup.storagePath());
    }

    @Test
    void storagePathCannotEscapeConfiguredRoot() {
        LocalIslandStorage storage = new LocalIslandStorage(root);

        assertThrows(IOException.class, () -> storage.openBundle("../outside/bundle.tar.zst"));
        assertThrows(IOException.class, () -> storage.readBundleManifest("../outside/bundle.tar.zst"));
    }

    private static IslandBundleManifest manifest(String reason) {
        return new IslandBundleManifest(
                ISLAND_ID,
                OWNER_ID,
                3,
                "1.21.11",
                12,
                300,
                new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
                List.of("default"),
                List.of("shop"),
                List.of("plains"),
                CREATED_AT,
                SAVED_AT,
                "",
                "SHA-256",
                "zstd",
                "",
                0L,
                reason
        );
    }

    private static ByteArrayInputStream input(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static String checksum(byte[] bytes) throws IOException {
        return Sha256Checksums.of(input(bytes));
    }
}
