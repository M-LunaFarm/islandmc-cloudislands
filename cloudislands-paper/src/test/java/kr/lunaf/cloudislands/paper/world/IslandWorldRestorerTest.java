package kr.lunaf.cloudislands.paper.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class IslandWorldRestorerTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000301");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000302");
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");

    @TempDir
    Path stagingRoot;

    @Test
    void stageRejectsIncompatibleManifestBeforeExtraction() {
        AtomicBoolean extractorInvoked = new AtomicBoolean(false);
        IslandWorldRestorer restorer = new IslandWorldRestorer(
            new FixedManifestStorage(incompatibleManifest()),
            stagingRoot,
            new BundleRestorePlanner((bundleFile, targetDirectory) -> {
                extractorInvoked.set(true);
                throw new IOException("extractor should not run");
            })
        );

        IOException exception = assertThrows(IOException.class, () -> restorer.stage(ISLAND_ID, "ci_shard_001", 0, 0));

        assertTrue(exception.getMessage().contains("incompatible island bundle restore"));
        assertTrue(exception.getMessage().contains("minecraftDataVersion"));
        assertFalse(extractorInvoked.get());
    }

    private static IslandBundleManifest incompatibleManifest() {
        return new IslandBundleManifest(
            ISLAND_ID,
            OWNER_ID,
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            "1.21.99",
            12,
            300,
            new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            List.of("default"),
            List.of("shop"),
            List.of("minecraft:plains"),
            NOW,
            NOW,
            "checksum",
            BundleRestorePolicy.CHECKSUM_ALGORITHM,
            BundleRestorePolicy.COMPRESSION,
            "islands/" + ISLAND_ID + "/snapshots/000001/bundle.tar.zst",
            42L,
            "CREATED",
            true,
            BundleRestorePolicy.PLACEMENT_POLICY,
            BundleRestorePolicy.RESTORE_POLICY,
            "1.0.1",
            IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION + 1,
            "1.21.99",
            "skyblock-default@future"
        );
    }

    private static final class FixedManifestStorage implements IslandStorage {
        private final IslandBundleManifest manifest;

        private FixedManifestStorage(IslandBundleManifest manifest) {
            this.manifest = manifest;
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public IslandBundleManifest readManifest(UUID islandId) {
            return manifest;
        }

        @Override
        public Optional<IslandBundleManifest> readSnapshotManifest(UUID islandId, long snapshotNo) {
            return Optional.of(manifest);
        }

        @Override
        public Optional<IslandBundleManifest> readBundleManifest(String storagePath) {
            return Optional.of(manifest);
        }

        @Override
        public InputStream openLatestBundle(UUID islandId) {
            return new ByteArrayInputStream("not-a-real-bundle".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) {
            return openLatestBundle(islandId);
        }

        @Override
        public InputStream openBundle(String storagePath) {
            return openLatestBundle(ISLAND_ID);
        }

        @Override
        public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int pruneSnapshots(UUID islandId, int keepLatest) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void promoteSnapshot(UUID islandId, long snapshotNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteLiveState(UUID islandId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteIsland(UUID islandId) {
            throw new UnsupportedOperationException();
        }
    }
}
