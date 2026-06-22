package kr.lunaf.cloudislands.paper.world.export;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.integration.IntegrationLifecycleHooks;
import kr.lunaf.cloudislands.paper.world.cell.CellExtractionPlan;
import kr.lunaf.cloudislands.paper.world.cell.FileBackedCellTransfer;
import kr.lunaf.cloudislands.paper.world.cell.ShardCellTransferPlanner;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class ExternalTarIslandBundleExporter implements IslandBundleExporter {
    private final Path worldContainer;
    private final IntegrationLifecycleHooks integrationHooks;

    public ExternalTarIslandBundleExporter(Path worldContainer) {
        this(worldContainer, IntegrationLifecycleHooks.noop());
    }

    public ExternalTarIslandBundleExporter(Path worldContainer, IntegrationLifecycleHooks integrationHooks) {
        this.worldContainer = worldContainer;
        this.integrationHooks = integrationHooks == null ? IntegrationLifecycleHooks.noop() : integrationHooks;
    }

    @Override
    public ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory) throws IOException {
        return export(islandId, activeIsland, targetDirectory, null);
    }

    @Override
    public ExportedIslandBundle export(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path targetDirectory, IslandBundleManifest manifest) throws IOException {
        Files.createDirectories(targetDirectory);
        long snapshotNo = Instant.now().toEpochMilli();
        Path bundle = targetDirectory.resolve(String.format("%d-bundle.tar.zst", snapshotNo));
        while (Files.exists(bundle)) {
            snapshotNo++;
            bundle = targetDirectory.resolve(String.format("%d-bundle.tar.zst", snapshotNo));
        }
        Path staging = targetDirectory.resolve("cell-stage");
        deleteDirectory(staging);
        Files.createDirectories(staging);
        Files.createDirectories(staging.resolve("chunks"));
        Files.createDirectories(staging.resolve("entities"));
        Files.createDirectories(staging.resolve("block-entities"));
        CellExtractionPlan extraction = new ShardCellTransferPlanner(activeIsland.islandSize())
            .extraction(islandId, activeIsland.worldName(), activeIsland.originX(), activeIsland.originZ(), staging.resolve("chunks"));
        new FileBackedCellTransfer(worldContainer).extract(extraction);
        IntegrationLifecycleHooks.LifecycleBatch integrations = integrationHooks.exportState(islandId, activeIsland, snapshotNo, bundle);
        integrations.throwIfFailed();
        integrations.writeIfPresent(staging.resolve("integrations/export.json"));
        writeStagedManifest(islandId, activeIsland, staging.resolve("manifest.json"), manifest);
        writeStagedChecksums(staging);
        ProcessBuilder processBuilder = new ProcessBuilder("tar", "--zstd", "-cf", bundle.toAbsolutePath().toString(), "-C", staging.toAbsolutePath().toString(), ".");
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("bundle export failed with exit code " + exitCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("bundle export interrupted", exception);
        }
        return new ExportedIslandBundle(islandId, bundle, snapshotNo);
    }

    private void writeStagedManifest(UUID islandId, ActiveIslandRegistry.ActiveIsland activeIsland, Path manifestPath, IslandBundleManifest source) throws IOException {
        Instant now = Instant.now();
        IslandBundleManifest manifest = new IslandBundleManifest(
            islandId,
            source == null ? new UUID(0L, 0L) : source.ownerUuid(),
            source == null ? 3 : source.formatVersion(),
            source == null ? "unknown" : source.minecraftVersion(),
            source == null ? (int) activeIsland.schemaVersion() : source.schemaVersion(),
            source == null ? activeIsland.islandSize() : source.size(),
            new IslandLocation(activeIsland.worldName(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            source == null ? List.of() : source.homes(),
            source == null ? List.of() : source.warps(),
            source == null ? List.of() : source.biomes(),
            source == null ? now : source.createdAt(),
            now,
            "",
            "SHA-256",
            "zstd",
            "",
            0L,
            source == null ? "" : source.snapshotReason(),
            source == null || source.portable(),
            source == null ? "node-agnostic-shard-cell-remap" : source.placementPolicy(),
            source == null ? "verify-checksum-then-restore-to-current-active-node" : source.restorePolicy()
        );
        Files.writeString(manifestPath, IslandManifestJson.write(manifest), StandardCharsets.UTF_8);
    }

    private void writeStagedChecksums(Path staging) throws IOException {
        Path checksums = staging.resolve("checksums.sha256");
        List<Path> files = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(staging)) {
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in island bundles: " + staging.relativize(path));
                }
                if (!path.equals(checksums)) {
                    files.add(path);
                }
            }
        }
        files.sort(Comparator.comparing(path -> staging.relativize(path).toString().replace('\\', '/')));
        StringBuilder builder = new StringBuilder();
        for (Path file : files) {
            String checksum;
            try (InputStream input = Files.newInputStream(file)) {
                checksum = Sha256Checksums.of(input);
            }
            builder.append(checksum)
                .append("  ")
                .append(staging.relativize(file).toString().replace('\\', '/'))
                .append('\n');
        }
        Files.writeString(checksums, builder.toString(), StandardCharsets.UTF_8);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
