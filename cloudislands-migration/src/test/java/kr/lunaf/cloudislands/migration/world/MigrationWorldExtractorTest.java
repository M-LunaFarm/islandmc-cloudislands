package kr.lunaf.cloudislands.migration.world;

import kr.lunaf.cloudislands.migration.MigrationManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationWorldExtractorTest {
    private static final UUID ISLAND_ID = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000902");

    @TempDir
    Path root;

    @Test
    void extractWritesBundleChecksumAndVerifiableWorldArchive() throws Exception {
        Path sourceWorld = root.resolve("SuperiorWorld");
        Files.createDirectories(sourceWorld.resolve("region"));
        Files.createDirectories(sourceWorld.resolve("entities"));
        Files.writeString(sourceWorld.resolve("level.dat"), "level", StandardCharsets.UTF_8);
        Files.writeString(sourceWorld.resolve("region").resolve("r.0.0.mca"), "region", StandardCharsets.UTF_8);
        Files.writeString(sourceWorld.resolve("entities").resolve("e.0.0.mca"), "entities", StandardCharsets.UTF_8);

        MigrationWorldExtractor extractor = new MigrationWorldExtractor();
        MigrationWorldExtractionPlan plan = extractor.plan(manifest(sourceWorld), root.resolve("target"));
        MigrationWorldBundle bundle = extractor.extract(plan);
        MigrationWorldBundle verified = extractor.verify(plan);

        assertEquals(ISLAND_ID, bundle.islandId());
        assertEquals(3L, bundle.fileCount());
        assertEquals(bundle.checksum(), verified.checksum());
        assertEquals(3L, verified.fileCount());
        assertTrue(Files.isRegularFile(bundle.bundlePath()));
        assertTrue(Files.isRegularFile(bundle.manifestPath()));
        assertEquals(bundle.checksum() + "  bundle.zip\n", Files.readString(bundle.bundlePath().resolveSibling("checksums.sha256"), StandardCharsets.UTF_8));
        assertEquals(bundle.checksum(), IslandManifestJson.read(Files.readString(bundle.manifestPath())).checksum());
        assertEquals(OWNER_ID, IslandManifestJson.read(Files.readString(bundle.manifestPath())).ownerUuid());
    }

    @Test
    void verifyRejectsTamperedMigrationBundle() throws Exception {
        Path sourceWorld = root.resolve("SuperiorWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "level", StandardCharsets.UTF_8);

        MigrationWorldExtractor extractor = new MigrationWorldExtractor();
        MigrationWorldExtractionPlan plan = extractor.plan(manifest(sourceWorld), root.resolve("target"));
        extractor.extract(plan);
        Files.writeString(plan.targetBundlePath(), "tampered", StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> extractor.verify(plan));

        assertEquals("migration bundle checksum mismatch for " + ISLAND_ID, exception.getMessage());
    }

    @Test
    void verifyRejectsMismatchedBundleManifestOwner() throws Exception {
        Path sourceWorld = root.resolve("SuperiorWorld");
        Files.createDirectories(sourceWorld);
        Files.writeString(sourceWorld.resolve("level.dat"), "level", StandardCharsets.UTF_8);

        MigrationWorldExtractor extractor = new MigrationWorldExtractor();
        MigrationWorldExtractionPlan plan = extractor.plan(manifest(sourceWorld), root.resolve("target"));
        MigrationWorldBundle bundle = extractor.extract(plan);
        IslandBundleManifest manifest = IslandManifestJson.read(Files.readString(bundle.manifestPath()));
        Files.writeString(bundle.manifestPath(), IslandManifestJson.write(new IslandBundleManifest(
                manifest.islandId(),
                UUID.fromString("00000000-0000-0000-0000-000000000999"),
                manifest.formatVersion(),
                manifest.minecraftVersion(),
                manifest.schemaVersion(),
                manifest.size(),
                manifest.spawn(),
                manifest.homes(),
                manifest.warps(),
                manifest.biomes(),
                manifest.createdAt(),
                manifest.savedAt(),
                manifest.checksum(),
                manifest.checksumAlgorithm(),
                manifest.compression(),
                manifest.storagePath(),
                manifest.sizeBytes(),
                manifest.snapshotReason(),
                manifest.portable(),
                manifest.placementPolicy(),
                manifest.restorePolicy()
        )));

        IOException exception = assertThrows(IOException.class, () -> extractor.verify(plan));

        assertEquals("migration manifest owner mismatch for " + ISLAND_ID, exception.getMessage());
    }

    @Test
    void planRequiresSourceWorldPathFromManifest() {
        MigrationWorldExtractor extractor = new MigrationWorldExtractor();
        MigrationManifest manifest = manifest(Path.of(""));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> extractor.plan(manifest, root.resolve("target")));

        assertEquals("manifest has no source world path: " + ISLAND_ID, exception.getMessage());
    }

    private static MigrationManifest manifest(Path sourceWorld) {
        return new MigrationManifest(
                ISLAND_ID,
                OWNER_ID,
                List.of(OWNER_ID),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "minecraft:plains",
                "0",
                true,
                false,
                300,
                10L,
                "2500",
                sourceWorld.toString()
        );
    }
}
