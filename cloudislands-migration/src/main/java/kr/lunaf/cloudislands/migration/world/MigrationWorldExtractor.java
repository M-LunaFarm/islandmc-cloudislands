package kr.lunaf.cloudislands.migration.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.migration.MigrationHome;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.storage.BundleRestorePolicy;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;

public final class MigrationWorldExtractor {
    public MigrationWorldExtractionPlan plan(MigrationManifest manifest, Path targetRoot) {
        if (manifest.sourceWorldPath() == null || manifest.sourceWorldPath().isBlank()) {
            throw new IllegalArgumentException("manifest has no source world path: " + manifest.islandId());
        }
        Path target = targetRoot
            .resolve("islands")
            .resolve(manifest.islandId().toString())
            .resolve("migration")
            .resolve("bundle.zip");
        return new MigrationWorldExtractionPlan(manifest.islandId(), Path.of(manifest.sourceWorldPath()), target, manifest);
    }

    public MigrationWorldBundle extract(MigrationWorldExtractionPlan plan) throws IOException {
        if (!Files.exists(plan.sourcePath())) {
            throw new IOException("source world does not exist: " + plan.sourcePath());
        }
        Files.createDirectories(plan.targetBundlePath().getParent());
        long fileCount = writeZip(plan.sourcePath(), plan.targetBundlePath());
        String checksum = sha256(plan.targetBundlePath());
        long sizeBytes = Files.size(plan.targetBundlePath());
        Files.writeString(plan.targetBundlePath().resolveSibling("checksums.sha256"), checksum + "  " + plan.targetBundlePath().getFileName() + "\n");
        Path manifestPath = writeManifest(plan, checksum, sizeBytes);
        return new MigrationWorldBundle(plan.islandId(), plan.sourcePath(), plan.targetBundlePath(), manifestPath, checksum, sizeBytes, fileCount);
    }

    public MigrationWorldBundle verify(MigrationWorldExtractionPlan plan) throws IOException {
        if (!Files.isRegularFile(plan.targetBundlePath())) {
            throw new IOException("migration bundle does not exist: " + plan.targetBundlePath());
        }
        Path checksumFile = plan.targetBundlePath().resolveSibling("checksums.sha256");
        if (!Files.isRegularFile(checksumFile)) {
            throw new IOException("migration checksum file does not exist: " + checksumFile);
        }
        String expected = Files.readString(checksumFile).trim().split("\\s+")[0];
        String actual = sha256(plan.targetBundlePath());
        if (!expected.equalsIgnoreCase(actual)) {
            throw new IOException("migration bundle checksum mismatch for " + plan.islandId());
        }
        Path manifestPath = plan.targetBundlePath().resolveSibling("manifest.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException("migration manifest file does not exist: " + manifestPath);
        }
        IslandBundleManifest manifest = IslandManifestJson.read(Files.readString(manifestPath));
        if (!actual.equalsIgnoreCase(manifest.checksum())) {
            throw new IOException("migration manifest checksum mismatch for " + plan.islandId());
        }
        if (!manifest.portable()) {
            throw new IOException("migration manifest is not portable for " + plan.islandId());
        }
        verifyManifestIdentity(plan, manifest);
        return new MigrationWorldBundle(plan.islandId(), plan.sourcePath(), plan.targetBundlePath(), manifestPath, actual, Files.size(plan.targetBundlePath()), countZipEntries(plan.targetBundlePath()));
    }

    private long writeZip(Path source, Path target) throws IOException {
        try (OutputStream fileOutput = Files.newOutputStream(target);
             ZipOutputStream zip = new ZipOutputStream(fileOutput)) {
            if (Files.isRegularFile(source)) {
                addFile(zip, source, source.getFileName().toString());
                return 1L;
            }
            try (Stream<Path> files = Files.walk(source)) {
                final long[] count = {0L};
                files.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(file -> {
                        try {
                            Path relative = source.relativize(file);
                            addFile(zip, file, relative.toString().replace('\\', '/'));
                            count[0]++;
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
                return count[0];
            } catch (IllegalStateException exception) {
                if (exception.getCause() instanceof IOException io) {
                    throw io;
                }
                throw exception;
            }
        }
    }

    private void addFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(Files.getLastModifiedTime(file).toMillis());
        zip.putNextEntry(entry);
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is not available", exception);
        }
    }

    private long countZipEntries(Path path) throws IOException {
        long count = 0L;
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(path))) {
            while (input.getNextEntry() != null) {
                count++;
                input.closeEntry();
            }
        }
        return count;
    }

    private void verifyManifestIdentity(MigrationWorldExtractionPlan plan, IslandBundleManifest manifest) throws IOException {
        if (!plan.islandId().equals(manifest.islandId())) {
            throw new IOException("migration manifest island id mismatch for " + plan.islandId());
        }
        MigrationManifest source = plan.manifest();
        if (source == null) {
            return;
        }
        if (!source.ownerUuid().equals(manifest.ownerUuid())) {
            throw new IOException("migration manifest owner mismatch for " + plan.islandId());
        }
        if (source.size() != manifest.size()) {
            throw new IOException("migration manifest size mismatch for " + plan.islandId());
        }
    }

    private Path writeManifest(MigrationWorldExtractionPlan plan, String checksum, long sizeBytes) throws IOException {
        MigrationManifest source = plan.manifest();
        UUID ownerUuid = source == null ? new UUID(0L, 0L) : source.ownerUuid();
        int islandSize = source == null ? 300 : source.size();
        Instant now = Instant.now();
        IslandBundleManifest manifest = new IslandBundleManifest(
                plan.islandId(),
                ownerUuid,
                3,
                "migration",
                12,
                islandSize,
                spawnLocation(plan),
                homeNames(source),
                warpNames(source),
                biomeKeys(source),
                now,
                now,
                checksum,
                BundleRestorePolicy.CHECKSUM_ALGORITHM,
                "zip",
                storagePath(plan),
                sizeBytes,
                "SUPERIOR_SKYBLOCK2_MIGRATION"
        );
        Path manifestPath = plan.targetBundlePath().resolveSibling("manifest.json");
        Files.writeString(manifestPath, IslandManifestJson.write(manifest));
        return manifestPath;
    }

    private IslandLocation spawnLocation(MigrationWorldExtractionPlan plan) {
        MigrationManifest source = plan.manifest();
        if (source != null && source.homes() != null && !source.homes().isEmpty()) {
            MigrationHome home = source.homes().get(0);
            return new IslandLocation(home.worldName(), home.x(), home.y(), home.z(), home.yaw(), home.pitch());
        }
        return new IslandLocation(plan.sourcePath().getFileName().toString(), 0.5D, 100.0D, 0.5D, 180.0F, 0.0F);
    }

    private List<String> homeNames(MigrationManifest source) {
        if (source == null || source.homes() == null) {
            return List.of();
        }
        return source.homes().stream().map(MigrationHome::name).filter(name -> name != null && !name.isBlank()).toList();
    }

    private List<String> warpNames(MigrationManifest source) {
        if (source == null || source.warps() == null) {
            return List.of();
        }
        return source.warps().stream().map(warp -> warp.name()).filter(name -> name != null && !name.isBlank()).toList();
    }

    private List<String> biomeKeys(MigrationManifest source) {
        if (source == null || source.biomeKey() == null || source.biomeKey().isBlank()) {
            return List.of();
        }
        return List.of(source.biomeKey());
    }

    private String storagePath(MigrationWorldExtractionPlan plan) {
        return "islands/" + plan.islandId() + "/migration/" + plan.targetBundlePath().getFileName();
    }

    public MigrationWorldBundle extract(UUID islandId, Path sourcePath, Path targetRoot) throws IOException {
        Path target = targetRoot.resolve("islands").resolve(islandId.toString()).resolve("migration").resolve("bundle.zip");
        return extract(new MigrationWorldExtractionPlan(islandId, sourcePath, target));
    }
}
