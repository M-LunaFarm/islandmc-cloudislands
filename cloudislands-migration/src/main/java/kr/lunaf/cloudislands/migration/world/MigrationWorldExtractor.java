package kr.lunaf.cloudislands.migration.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import kr.lunaf.cloudislands.migration.MigrationManifest;

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
        return new MigrationWorldExtractionPlan(manifest.islandId(), Path.of(manifest.sourceWorldPath()), target);
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
        return new MigrationWorldBundle(plan.islandId(), plan.sourcePath(), plan.targetBundlePath(), checksum, sizeBytes, fileCount);
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
        return new MigrationWorldBundle(plan.islandId(), plan.sourcePath(), plan.targetBundlePath(), actual, Files.size(plan.targetBundlePath()), countZipEntries(plan.targetBundlePath()));
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

    public MigrationWorldBundle extract(UUID islandId, Path sourcePath, Path targetRoot) throws IOException {
        Path target = targetRoot.resolve("islands").resolve(islandId.toString()).resolve("migration").resolve("bundle.zip");
        return extract(new MigrationWorldExtractionPlan(islandId, sourcePath, target));
    }
}
