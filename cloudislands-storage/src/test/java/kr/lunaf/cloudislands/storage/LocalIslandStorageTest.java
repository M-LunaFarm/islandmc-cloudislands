package kr.lunaf.cloudislands.storage;

import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void failedLocalWriteLeavesNoPublishedSnapshotOrLatestPointer() {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        byte[] bundle = "partial-bundle".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> storage.writeSnapshot(ISLAND_ID, 8L, failingInput(bundle, 4), manifest("AUTO_HOURLY")));

        Path islandRoot = root.resolve("islands").resolve(ISLAND_ID.toString());
        assertFalse(Files.exists(islandRoot.resolve("snapshots").resolve("000008")));
        assertFalse(Files.exists(islandRoot.resolve("latest")));
        assertFalse(Files.exists(islandRoot.resolve("manifest.json")));
    }

    @Test
    void startupRecoveryRemovesAbandonedLocalWriteArtifactsAndRepairsLiveManifest() throws Exception {
        Path islandRoot = root.resolve("islands").resolve(ISLAND_ID.toString());
        Path snapshotsRoot = islandRoot.resolve("snapshots");
        Files.createDirectories(snapshotsRoot.resolve(".staging-000009-crash"));
        Files.writeString(snapshotsRoot.resolve(".latest-" + UUID.randomUUID() + ".tmp"), "000009", StandardCharsets.UTF_8);
        Files.createDirectories(snapshotsRoot.resolve(".old-000007-crash"));

        Path snapshotDir = snapshotsRoot.resolve("000009");
        Files.createDirectories(snapshotDir);
        Files.writeString(snapshotDir.resolve("manifest.json"), IslandManifestJson.write(manifest("RECOVERY")), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), "000009", StandardCharsets.UTF_8);

        LocalIslandStorage storage = new LocalIslandStorage(root);

        assertTrue(storage.available());
        assertFalse(Files.exists(snapshotsRoot.resolve(".staging-000009-crash")));
        assertFalse(Files.exists(snapshotsRoot.resolve(".old-000007-crash")));
        try (var stream = Files.list(snapshotsRoot)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
        assertEquals("RECOVERY", storage.readManifest(ISLAND_ID).snapshotReason());
    }

    @Test
    void readManifestUsesLatestSnapshotWhenCompatibilityManifestIsStale() throws Exception {
        LocalIslandStorage storage = new LocalIslandStorage(root);
        storage.writeSnapshot(ISLAND_ID, 1L, input("old-bundle".getBytes(StandardCharsets.UTF_8)), manifest("OLD"));
        Path islandRoot = root.resolve("islands").resolve(ISLAND_ID.toString());
        Path latestSnapshot = islandRoot.resolve("snapshots").resolve("000002");
        Files.createDirectories(latestSnapshot);
        Files.writeString(latestSnapshot.resolve("manifest.json"), IslandManifestJson.write(manifest("LATEST")), StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), "000002", StandardCharsets.UTF_8);

        assertEquals("LATEST", storage.readManifest(ISLAND_ID).snapshotReason());
    }

    @Test
    void startupRecoveryRevertsCompatibilityManifestWhenLatestWasNotPublished() throws Exception {
        Path islandRoot = root.resolve("islands").resolve(ISLAND_ID.toString());
        Path oldSnapshot = islandRoot.resolve("snapshots").resolve("000001");
        Path newSnapshot = islandRoot.resolve("snapshots").resolve("000002");
        String oldManifest = IslandManifestJson.write(manifest("OLD"));
        String newManifest = IslandManifestJson.write(manifest("NEW"));
        Files.createDirectories(oldSnapshot);
        Files.createDirectories(newSnapshot);
        Files.writeString(oldSnapshot.resolve("manifest.json"), oldManifest, StandardCharsets.UTF_8);
        Files.writeString(newSnapshot.resolve("manifest.json"), newManifest, StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("manifest.json"), newManifest, StandardCharsets.UTF_8);
        Files.writeString(islandRoot.resolve("latest"), "000001", StandardCharsets.UTF_8);

        LocalIslandStorage storage = new LocalIslandStorage(root);

        assertTrue(storage.available());
        assertEquals("OLD", storage.readManifest(ISLAND_ID).snapshotReason());
        assertEquals(oldManifest, Files.readString(islandRoot.resolve("manifest.json"), StandardCharsets.UTF_8));
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

    @Test
    void manifestJsonPreservesRuntimeCompatibilityMetadata() {
        IslandBundleManifest manifest = new IslandBundleManifest(
                ISLAND_ID,
                OWNER_ID,
                3,
                "1.21.11",
                12,
                300,
                new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
                List.of("default"),
                List.of("shop"),
                List.of("minecraft:plains"),
                CREATED_AT,
                SAVED_AT,
                "checksum",
                "SHA-256",
                "zstd",
                "islands/" + ISLAND_ID + "/snapshots/000001/bundle.tar.zst",
                42L,
                "CREATED",
                true,
                BundleRestorePolicy.PLACEMENT_POLICY,
                BundleRestorePolicy.RESTORE_POLICY,
                "1.0.1",
                4435,
                "1.21.11",
                "skyblock-default@4"
        );

        String json = IslandManifestJson.write(manifest);
        IslandBundleManifest parsed = IslandManifestJson.read(json);

        assertTrue(json.contains("\"manifestSchemaVersion\":" + IslandManifestJson.CURRENT_MANIFEST_SCHEMA_VERSION));
        assertTrue(json.contains("\"pluginVersion\":\"1.0.1\""));
        assertTrue(json.contains("\"minecraftDataVersion\":4435"));
        assertTrue(json.contains("\"sourceMinecraftVersion\":\"1.21.11\""));
        assertTrue(json.contains("\"sourceAdapterId\":\"1.21.11\""));
        assertTrue(json.contains("\"bundleSchemaVersion\":3"));
        assertTrue(json.contains("\"worldDataVersion\":4435"));
        assertTrue(json.contains("\"minimumReaderVersion\":3"));
        assertTrue(json.contains("\"featureCapabilities\""));
        assertTrue(json.contains("\"restoreCompatibilitySummary\":\"compatible-current\""));
        assertTrue(json.contains("\"restoreMigrationAdapter\":\"" + BundleCompatibilityPolicy.TARGET_ADAPTER_ID + "\""));
        assertEquals("1.0.1", parsed.pluginVersion());
        assertEquals(4435, parsed.minecraftDataVersion());
        assertEquals("1.21.11", parsed.paperApiBaseline());
        assertEquals("skyblock-default@4", parsed.templateVersion());
        assertEquals("compatible-current", parsed.restoreCompatibilitySummary());
    }

    @Test
    void manifestJsonReadsCompatibilityAliasFields() {
        String json = IslandManifestJson.write(manifest("ALIAS"))
                .replace("\"formatVersion\":3", "\"formatVersion\":3")
                .replace("\"bundleSchemaVersion\":3", "\"bundleSchemaVersion\":2")
                .replace("\"minimumReaderVersion\":3", "\"minimumReaderVersion\":2")
                .replace("\"minecraftVersion\":\"1.21.11\"", "\"minecraftVersion\":\"1.21.11\"")
                .replace("\"sourceMinecraftVersion\":\"1.21.11\"", "\"sourceMinecraftVersion\":\"1.20.6\"")
                .replace("\"minecraftDataVersion\":0", "\"minecraftDataVersion\":0")
                .replace("\"worldDataVersion\":0", "\"worldDataVersion\":3839")
                .replace("\"paperApiBaseline\":\"1.21.11\"", "\"paperApiBaseline\":\"1.21.11\"")
                .replace("\"sourceAdapterId\":\"1.21.11\"", "\"sourceAdapterId\":\"1.20.6\"");

        IslandBundleManifest parsed = IslandManifestJson.read(json);

        assertEquals(2, parsed.formatVersion());
        assertEquals("1.20.6", parsed.minecraftVersion());
        assertEquals(3839, parsed.minecraftDataVersion());
        assertEquals("1.20.6", parsed.paperApiBaseline());
        assertEquals("compatible-upgrade:" + BundleCompatibilityPolicy.UPGRADE_ADAPTER_ID, parsed.restoreCompatibilitySummary());
    }

    @Test
    void manifestJsonReadsLegacyManifestsWithoutSchemaVersion() {
        String legacyJson = IslandManifestJson.write(manifest("LEGACY"))
                .replace("\"manifestSchemaVersion\":" + IslandManifestJson.CURRENT_MANIFEST_SCHEMA_VERSION + ",", "");

        IslandBundleManifest parsed = IslandManifestJson.read(legacyJson);

        assertEquals(ISLAND_ID, parsed.islandId());
        assertEquals("LEGACY", parsed.snapshotReason());
    }

    @Test
    void manifestJsonRejectsFutureManifestSchemaVersion() {
        String futureJson = IslandManifestJson.write(manifest("FUTURE"))
                .replace(
                        "\"manifestSchemaVersion\":" + IslandManifestJson.CURRENT_MANIFEST_SCHEMA_VERSION,
                        "\"manifestSchemaVersion\":" + (IslandManifestJson.CURRENT_MANIFEST_SCHEMA_VERSION + 1)
                );

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> IslandManifestJson.read(futureJson));

        assertTrue(exception.getMessage().contains("Unsupported island bundle manifest schema version"));
    }

    @Test
    void manifestJsonRejectsMalformedOrTrailingJson() {
        assertThrows(RuntimeException.class, () -> IslandManifestJson.read("{\"islandId\""));
        assertThrows(RuntimeException.class, () -> IslandManifestJson.read(IslandManifestJson.write(manifest("TRAILING")) + " true"));
    }

    @Test
    void manifestJsonMigratesLegacyBiomeKeysToNamespaceKeys() {
        String json = IslandManifestJson.write(manifest("LEGACY"))
                .replace("\"minecraft:plains\"", "\"plains\"");

        IslandBundleManifest parsed = IslandManifestJson.read(json);

        assertEquals(List.of("minecraft:plains"), parsed.biomes());
    }

    @Test
    void restorePreflightRejectsFutureBundleCompatibilityMetadata() {
        IslandBundleManifest manifest = new IslandBundleManifest(
                ISLAND_ID,
                OWNER_ID,
                IslandBundleManifest.CURRENT_FORMAT_VERSION + 1,
                "1.21.99",
                12,
                300,
                new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
                List.of("default"),
                List.of("shop"),
                List.of("minecraft:plains"),
                CREATED_AT,
                SAVED_AT,
                "checksum",
                "SHA-256",
                "zstd",
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

        assertEquals(List.of("formatVersion", "minecraftDataVersion"), manifest.restoreMissingRequirements());
        assertEquals("missing-formatVersion+minecraftDataVersion", manifest.restorePreflightSummary());
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
                List.of("minecraft:plains"),
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

    private static InputStream failingInput(byte[] bytes, int failAfterBytes) {
        return new InputStream() {
            private int offset;

            @Override
            public int read() throws IOException {
                if (offset >= failAfterBytes) {
                    throw new IOException("simulated local write failure");
                }
                if (offset >= bytes.length) {
                    return -1;
                }
                return bytes[offset++] & 0xff;
            }
        };
    }

    private static String checksum(byte[] bytes) throws IOException {
        return Sha256Checksums.of(input(bytes));
    }
}
