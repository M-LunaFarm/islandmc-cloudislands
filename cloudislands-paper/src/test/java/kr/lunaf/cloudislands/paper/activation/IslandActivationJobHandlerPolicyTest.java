package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Map.entry;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.common.protection.RegionIndex;
import kr.lunaf.cloudislands.paper.ProtectionController;
import kr.lunaf.cloudislands.paper.cache.LocalIslandPermissionCache;
import kr.lunaf.cloudislands.paper.world.IslandWorldRestorer;
import kr.lunaf.cloudislands.paper.world.bundle.BundleExtractor;
import kr.lunaf.cloudislands.paper.world.bundle.BundleRestorePlanner;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.export.IslandBundleExporter;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IslandActivationJobHandlerPolicyTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000001201");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000001202");
    private static final Instant NOW = Instant.parse("2026-06-17T00:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void templateBundleCreateFailsClosedWhenRestoreOrPlacementIsUnavailable() throws Exception {
        ShardWorldManager shardWorldManager = new ShardWorldManager("ci_shard_", 1, 1024);
        IslandActivationJobHandler handler = new IslandActivationJobHandler(
            new TemplateBundleStorage(compatibleManifest(), "template".getBytes(StandardCharsets.UTF_8)),
            shardWorldManager,
            protectionController(),
            null,
            null,
            0,
            new FileBackedCellTransfer(tempDir.resolve("worlds"))
        );

        IslandActivationJobHandler.ActivationResult result = handler.handle(createJob("templates/default.tar.zst", "checksum"));

        assertFalse(result.success());
        assertEquals("ERROR_ACTIVATING", result.state());
        assertFalse(shardWorldManager.reserved(ISLAND_ID), "failed bundled creates must release the reserved shard cell");
    }

    @Test
    void bundledCreateStagesPlacesProtectsAndSnapshotsTemplateBundle() throws Exception {
        byte[] bundlePayload = "portable-template-bundle".getBytes(StandardCharsets.UTF_8);
        String checksum = Sha256Checksums.of(new ByteArrayInputStream(bundlePayload));
        IslandBundleManifest manifest = compatibleManifest()
            .withStoredBundle(checksum, BundleRestorePolicy.CHECKSUM_ALGORITHM, BundleRestorePolicy.COMPRESSION, "templates/default.tar.zst", bundlePayload.length);
        TemplateBundleStorage storage = new TemplateBundleStorage(manifest, bundlePayload);
        Path worldContainer = tempDir.resolve("worlds");
        ProtectionController protection = protectionController();
        IslandActivationJobHandler handler = new IslandActivationJobHandler(
            storage,
            new ShardWorldManager("ci_shard_", 1, 1024),
            protection,
            new IslandWorldRestorer(storage, tempDir.resolve("staging"), templateRestorePlanner(manifest)),
            null,
            0,
            new FileBackedCellTransfer(worldContainer),
            new ActiveIslandRegistry(),
            new IslandSaveService(storage, creationSnapshotExporter(), tempDir.resolve("exports")),
            64
        );

        IslandActivationJobHandler.ActivationResult result = handler.handle(createJob("templates/default.tar.zst", checksum));

        assertTrue(result.success());
        assertEquals("ACTIVE", result.state());
        assertEquals("ci_shard_001", result.worldName());
        assertTrue(Files.isRegularFile(worldContainer.resolve("ci_shard_001/region/r.0.0.mca")), "template region data must be placed into the shard world");
        assertTrue(protection.region(ISLAND_ID).isPresent(), "template-created islands must register protection after placement");
        assertEquals(1L, result.creationSnapshotNo());
        assertEquals("created-checksum", result.creationSnapshotChecksum());
        assertEquals("CREATED", storage.lastSnapshotReason);
    }

    @Test
    void templateBundleCreatePolicyKeepsExplicitRestoreSignals() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/activation/IslandActivationJobHandler.java"), StandardCharsets.UTF_8);

        assertTrue(source.contains("job.type() == IslandJobType.CREATE_ISLAND && !job.payload().getOrDefault(\"templateBundlePath\", \"\").isBlank()"), "bundled CREATE_ISLAND jobs must use the template bundle branch");
        assertTrue(source.contains("throw new IOException(\"template bundle restore is unavailable"), "bundled create must fail when no world restorer is wired");
        assertTrue(source.contains("throw new IOException(\"template bundle placement is unavailable"), "bundled create must fail when no cell transfer is wired");
        assertTrue(source.contains("worldRestorer.stageTemplateBundle"), "bundled create must stage the configured template bundle");
        assertTrue(source.contains("cellTransfer.place(placement)"), "staged template bundles must be placed into the shard world cell");
    }

    @Test
    void activationUsesAuthoritativeCoreSizeInsteadOfStaleBundleSize() {
        ShardWorldManager shardWorldManager = new ShardWorldManager("ci_shard_", 1, 1024);
        IslandActivationJobHandler handler = new IslandActivationJobHandler(
            new TemplateBundleStorage(compatibleManifest(), new byte[0]),
            shardWorldManager,
            protectionController()
        );
        IslandJob job = new IslandJob(
            UUID.randomUUID(),
            IslandJobType.ACTIVATE_ISLAND,
            ISLAND_ID,
            "island-node-1",
            0,
            Map.of("islandSize", "400", "fencingToken", "21"),
            NOW
        );

        IslandActivationJobHandler.ActivationResult result = handler.handle(job);

        assertTrue(result.success());
        assertEquals(400, result.islandSize());
    }

    @Test
    void activationFailsBeforeReservationWhenIslandWouldOverlapAdjacentCells() {
        ShardWorldManager shardWorldManager = new ShardWorldManager("ci_shard_", 1, 1024);
        IslandActivationJobHandler handler = new IslandActivationJobHandler(
            new TemplateBundleStorage(compatibleManifest().withSize(1024), new byte[0]),
            shardWorldManager,
            protectionController()
        );
        IslandJob job = new IslandJob(
            UUID.randomUUID(),
            IslandJobType.ACTIVATE_ISLAND,
            ISLAND_ID,
            "island-node-1",
            0,
            Map.of("islandSize", "1024", "fencingToken", "22"),
            NOW
        );

        IslandActivationJobHandler.ActivationResult result = handler.handle(job);

        assertFalse(result.success());
        assertEquals("ISLAND_SIZE_EXCEEDS_CELL", result.state());
        assertFalse(shardWorldManager.reserved(ISLAND_ID));
    }

    private static ProtectionController protectionController() {
        return new ProtectionController(new RegionIndex(), new LocalIslandPermissionCache());
    }

    private static IslandJob createJob(String bundlePath, String checksum) {
        return new IslandJob(
            UUID.fromString("00000000-0000-0000-0000-000000001203"),
            IslandJobType.CREATE_ISLAND,
            ISLAND_ID,
            "island-node-1",
            0,
            Map.ofEntries(
                entry("templateId", "default"),
                entry("ownerUuid", OWNER_ID.toString()),
                entry("islandSize", "64"),
                entry("worldName", "ci_shard_001"),
                entry("cellX", "0"),
                entry("cellZ", "0"),
                entry("fencingToken", "19"),
                entry("templateBundlePath", bundlePath),
                entry("templateBundleChecksum", checksum),
                entry("templateSchemaVersion", "12"),
                entry("homeName", "arrival"),
                entry("localX", "8.5"),
                entry("localY", "96.0"),
                entry("localZ", "-7.5"),
                entry("yaw", "45.0"),
                entry("pitch", "10.0")
            ),
            NOW
        );
    }

    private static BundleRestorePlanner templateRestorePlanner(IslandBundleManifest manifest) {
        return new BundleRestorePlanner((bundleFile, targetDirectory) -> {
            Files.createDirectories(targetDirectory.resolve("chunks"));
            Files.writeString(targetDirectory.resolve("chunks/r.0.0.mca"), "region", StandardCharsets.UTF_8);
            Path manifestPath = targetDirectory.resolve("manifest.json");
            Files.writeString(manifestPath, IslandManifestJson.write(manifest), StandardCharsets.UTF_8);
            return new BundleExtractor.ExtractedBundle(targetDirectory, manifestPath, targetDirectory.resolve("chunks"));
        });
    }

    private static IslandBundleExporter creationSnapshotExporter() {
        return (islandId, activeIsland, targetDirectory) -> {
            Files.createDirectories(targetDirectory);
            Path bundle = targetDirectory.resolve("created.tar.zst");
            Files.writeString(bundle, "created-bundle", StandardCharsets.UTF_8);
            return new IslandBundleExporter.ExportedIslandBundle(islandId, bundle, 1L);
        };
    }

    private static IslandBundleManifest compatibleManifest() {
        return new IslandBundleManifest(
            ISLAND_ID,
            OWNER_ID,
            IslandBundleManifest.CURRENT_FORMAT_VERSION,
            "1.21.11",
            12,
            64,
            new IslandLocation("ci_shard_001", 8.5D, 96.0D, -7.5D, 45.0F, 10.0F),
            List.of("arrival"),
            List.of(),
            List.of("minecraft:plains"),
            NOW,
            NOW,
            "",
            BundleRestorePolicy.CHECKSUM_ALGORITHM,
            BundleRestorePolicy.COMPRESSION,
            "templates/default.tar.zst",
            42L,
            "TEMPLATE",
            true,
            BundleRestorePolicy.PLACEMENT_POLICY,
            BundleRestorePolicy.RESTORE_POLICY,
            "1.0.1",
            IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION,
            "1.21.11",
            "skyblock-default@current"
        );
    }

    private static final class TemplateBundleStorage implements IslandStorage {
        private final IslandBundleManifest manifest;
        private final byte[] bundlePayload;
        private String lastSnapshotReason = "";

        private TemplateBundleStorage(IslandBundleManifest manifest, byte[] bundlePayload) {
            this.manifest = manifest;
            this.bundlePayload = bundlePayload;
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
        public Optional<IslandBundleManifest> readBundleManifest(String storagePath) {
            return Optional.of(manifest);
        }

        @Override
        public InputStream openLatestBundle(UUID islandId) {
            return new ByteArrayInputStream(bundlePayload);
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
            lastSnapshotReason = manifest.snapshotReason();
            return new StoredBundle("created-checksum", 14L, "islands/" + islandId + "/snapshots/" + snapshotNo + "/bundle.tar.zst", "SHA-256", "zstd");
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
        public void promoteSnapshot(UUID islandId, long snapshotNo) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) {
            throw new UnsupportedOperationException();
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
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteIsland(UUID islandId) {
            throw new UnsupportedOperationException();
        }
    }
}
