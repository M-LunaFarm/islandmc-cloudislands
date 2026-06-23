package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.storage.compression.BundleCompressionPolicy;
import kr.lunaf.cloudislands.storage.manifest.IslandManifestJson;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public final class LocalIslandStorage implements IslandStorage {
    private static final String STAGING_PREFIX = ".staging-";
    private static final String OLD_PREFIX = ".old-";
    private static final String TEMP_SUFFIX = ".tmp";

    private final Path root;
    private volatile boolean recovered;

    public LocalIslandStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public boolean available() throws IOException {
        ensureRecovered();
        Path islandsRoot = root.resolve("islands");
        Files.createDirectories(islandsRoot);
        return Files.isDirectory(islandsRoot) && Files.isWritable(islandsRoot);
    }

    @Override
    public IslandBundleManifest readManifest(UUID islandId) throws IOException {
        ensureRecovered();
        Path islandRoot = islandRoot(islandId);
        Path latest = islandRoot.resolve("latest");
        if (Files.exists(latest)) {
            String latestSnapshot = Files.readString(latest, StandardCharsets.UTF_8).trim();
            if (!latestSnapshot.isBlank()) {
                Path snapshotManifest = islandRoot.resolve("snapshots").resolve(latestSnapshot).resolve("manifest.json");
                if (Files.exists(snapshotManifest)) {
                    return IslandManifestJson.read(Files.readString(snapshotManifest, StandardCharsets.UTF_8));
                }
            }
        }
        Path compatibilityManifest = islandRoot.resolve("manifest.json");
        if (!Files.exists(compatibilityManifest)) {
            throw new IOException("missing island manifest: " + islandId);
        }
        return IslandManifestJson.read(Files.readString(compatibilityManifest, StandardCharsets.UTF_8));
    }

    @Override
    public Optional<IslandBundleManifest> readSnapshotManifest(UUID islandId, long snapshotNo) throws IOException {
        ensureRecovered();
        Path manifest = islandRoot(islandId).resolve("snapshots").resolve(String.format("%06d", snapshotNo)).resolve("manifest.json");
        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        return Optional.of(IslandManifestJson.read(Files.readString(manifest, StandardCharsets.UTF_8)));
    }

    @Override
    public Optional<IslandBundleManifest> readBundleManifest(String storagePath) throws IOException {
        ensureRecovered();
        Path manifest = resolveStoragePath(storagePath).resolveSibling("manifest.json");
        if (!Files.exists(manifest)) {
            return Optional.empty();
        }
        return Optional.of(IslandManifestJson.read(Files.readString(manifest, StandardCharsets.UTF_8)));
    }

    @Override
    public InputStream openLatestBundle(UUID islandId) throws IOException {
        ensureRecovered();
        String latest = Files.readString(islandRoot(islandId).resolve("latest"), StandardCharsets.UTF_8).trim();
        return Files.newInputStream(islandRoot(islandId).resolve("snapshots").resolve(latest).resolve("bundle.tar.zst"));
    }

    @Override
    public InputStream openSnapshotBundle(UUID islandId, long snapshotNo) throws IOException {
        ensureRecovered();
        return Files.newInputStream(islandRoot(islandId).resolve("snapshots").resolve(String.format("%06d", snapshotNo)).resolve("bundle.tar.zst"));
    }

    @Override
    public InputStream openBundle(String storagePath) throws IOException {
        ensureRecovered();
        return Files.newInputStream(resolveStoragePath(storagePath));
    }

    @Override
    public StoredBundle writeSnapshot(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path islandRoot = islandRoot(islandId);
        Path snapshotDir = islandRoot.resolve("snapshots").resolve(String.format("%06d", snapshotNo));
        return writeBundle(islandRoot, snapshotDir, bundle, manifest, true);
    }

    @Override
    public StoredBundle writeDeleteBackup(UUID islandId, long snapshotNo, InputStream bundle, IslandBundleManifest manifest) throws IOException {
        Path islandRoot = islandRoot(islandId);
        Path backupDir = islandRoot.resolve("backups").resolve("delete-" + String.format("%06d", snapshotNo));
        return writeBundle(islandRoot, backupDir, bundle, manifest, false);
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo) throws IOException {
        return writeDeleteBackupFromLatest(islandId, snapshotNo, "BEFORE_DELETE");
    }

    @Override
    public StoredBundle writeDeleteBackupFromLatest(UUID islandId, long snapshotNo, String reason) throws IOException {
        IslandBundleManifest manifest = readManifest(islandId);
        try (InputStream input = openLatestBundle(islandId)) {
            return writeDeleteBackup(islandId, snapshotNo, input, manifest.withSnapshotReason(reason));
        }
    }

    @Override
    public void promoteSnapshot(UUID islandId, long snapshotNo) throws IOException {
        ensureRecovered();
        Path islandRoot = islandRoot(islandId);
        String snapshot = String.format("%06d", snapshotNo);
        Path snapshotManifest = islandRoot.resolve("snapshots").resolve(snapshot).resolve("manifest.json");
        if (!Files.exists(snapshotManifest)) {
            throw new IOException("missing snapshot manifest: " + islandId + " #" + snapshotNo);
        }
        publishLiveState(islandRoot, snapshot, Files.readString(snapshotManifest, StandardCharsets.UTF_8));
    }

    @Override
    public void promoteBundle(UUID islandId, long snapshotNo, String storagePath) throws IOException {
        ensureRecovered();
        Path islandRoot = islandRoot(islandId);
        String snapshot = String.format("%06d", snapshotNo);
        Path sourceBundle = resolveStoragePath(storagePath);
        Path sourceManifest = sourceBundle.resolveSibling("manifest.json");
        if (!Files.exists(sourceBundle) || !Files.exists(sourceManifest)) {
            throw new IOException("missing stored bundle: " + storagePath);
        }
        Path snapshotDir = islandRoot.resolve("snapshots").resolve(snapshot);
        Files.createDirectories(snapshotDir.getParent());
        Path stagingDir = snapshotDir.getParent().resolve(STAGING_PREFIX + snapshotDir.getFileName() + "-" + UUID.randomUUID());
        Files.createDirectories(stagingDir);
        try {
            copyFileAtomically(sourceBundle, stagingDir.resolve("bundle.tar.zst"));
            IslandBundleManifest source = IslandManifestJson.read(Files.readString(sourceManifest, StandardCharsets.UTF_8));
            String compression = BundleCompressionPolicy.normalize(source.compression());
            String bundleFileName = BundleCompressionPolicy.bundleFileName(compression);
            Path stagedBundle = stagingDir.resolve(bundleFileName);
            long sizeBytes = Files.size(stagedBundle);
            String checksum = checksumOf(stagedBundle);
            IslandBundleManifest promoted = source.withStoredBundle(checksum, "SHA-256", compression, normalizedStoragePath(snapshotDir.resolve(bundleFileName)), sizeBytes);
            atomicWriteString(stagingDir.resolve("manifest.json"), IslandManifestJson.write(promoted));
            atomicWriteString(stagingDir.resolve("checksums.sha256"), checksum + "  " + bundleFileName + "\n");
            validateSnapshotDirectory(stagingDir, bundleFileName, promoted);
            fsyncDirectory(stagingDir);
            publishDirectoryAtomically(stagingDir, snapshotDir);
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(stagingDir);
            throw exception;
        }
        publishLiveState(islandRoot, snapshot, Files.readString(snapshotDir.resolve("manifest.json"), StandardCharsets.UTF_8));
    }

    private StoredBundle writeBundle(Path islandRoot, Path snapshotDir, InputStream bundle, IslandBundleManifest manifest, boolean updateLatest) throws IOException {
        ensureRecovered();
        Files.createDirectories(islandRoot);
        Files.createDirectories(snapshotDir.getParent());
        String compression = BundleCompressionPolicy.normalize(manifest.compression());
        String bundleFileName = BundleCompressionPolicy.bundleFileName(compression);
        Path stagingDir = snapshotDir.getParent().resolve(STAGING_PREFIX + snapshotDir.getFileName() + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(stagingDir);
            Path stagedBundle = stagingDir.resolve(bundleFileName);
            ChecksumCopy copied = writeStreamAtomically(bundle, stagedBundle);
            String storagePath = normalizedStoragePath(snapshotDir.resolve(bundleFileName));
            IslandBundleManifest savedManifest = manifest.withStoredBundle(copied.checksum(), "SHA-256", compression, storagePath, copied.sizeBytes());
            atomicWriteString(stagingDir.resolve("manifest.json"), IslandManifestJson.write(savedManifest));
            atomicWriteString(stagingDir.resolve("checksums.sha256"), copied.checksum() + "  " + bundleFileName + "\n");
            validateSnapshotDirectory(stagingDir, bundleFileName, savedManifest);
            fsyncDirectory(stagingDir);
            publishDirectoryAtomically(stagingDir, snapshotDir);
            if (updateLatest) {
                publishLiveState(islandRoot, snapshotDir.getFileName().toString(), IslandManifestJson.write(savedManifest));
            }
            return new StoredBundle(copied.checksum(), copied.sizeBytes(), storagePath, "SHA-256", compression);
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(stagingDir);
            throw exception;
        }
    }

    @Override
    public int pruneSnapshots(UUID islandId, int keepLatest) throws IOException {
        if (keepLatest < 1) {
            throw new IllegalArgumentException("keepLatest must be positive");
        }
        Path snapshotsRoot = islandRoot(islandId).resolve("snapshots");
        if (!Files.exists(snapshotsRoot)) {
            return 0;
        }
        List<Path> snapshots;
        try (var stream = Files.list(snapshotsRoot)) {
            snapshots = stream
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .toList();
        }
        String latest = latestSnapshotName(islandId);
        int deleted = 0;
        for (int index = keepLatest; index < snapshots.size(); index++) {
            if (snapshots.get(index).getFileName().toString().equals(latest)) {
                continue;
            }
            deleteRecursively(snapshots.get(index));
            deleted++;
        }
        return deleted;
    }

    @Override
    public int pruneSnapshots(UUID islandId, SnapshotRetentionPolicy policy) throws IOException {
        SnapshotRetentionPolicy effectivePolicy = policy == null ? SnapshotRetentionPolicy.defaultPolicy() : policy.normalized();
        Path snapshotsRoot = islandRoot(islandId).resolve("snapshots");
        if (!Files.exists(snapshotsRoot)) {
            return 0;
        }
        List<Path> snapshots;
        try (var stream = Files.list(snapshotsRoot)) {
            snapshots = stream
                .filter(Files::isDirectory)
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .toList();
        }
        Set<Path> retained = new HashSet<>();
        String latest = latestSnapshotName(islandId);
        for (Path snapshot : snapshots) {
            if (snapshot.getFileName().toString().equals(latest)) {
                retained.add(snapshot);
                break;
            }
        }
        int manualKept = 0;
        for (Path snapshot : snapshots) {
            if (manualSnapshot(snapshot) && manualKept < effectivePolicy.keepManual()) {
                retained.add(snapshot);
                manualKept++;
            }
        }
        retainAutomaticSnapshots(snapshots, retained, effectivePolicy);
        int deleted = 0;
        for (Path snapshot : snapshots) {
            if (!retained.contains(snapshot)) {
                deleteRecursively(snapshot);
                deleted++;
            }
        }
        return deleted;
    }

    private void retainAutomaticSnapshots(List<Path> snapshots, Set<Path> retained, SnapshotRetentionPolicy policy) {
        retainAutomaticBucket(snapshots, retained, policy.keepHourly(), "hour");
        retainAutomaticBucket(snapshots, retained, policy.keepDaily(), "day");
        retainAutomaticBucket(snapshots, retained, policy.keepWeekly(), "week");
    }

    private void retainAutomaticBucket(List<Path> snapshots, Set<Path> retained, int limit, String bucketType) {
        if (limit <= 0) {
            return;
        }
        Set<String> retainedBuckets = new HashSet<>();
        for (Path snapshot : snapshots) {
            if (retained.contains(snapshot) || manualSnapshot(snapshot)) {
                continue;
            }
            String bucket = snapshotBucket(snapshot, bucketType);
            if (retainedBuckets.add(bucket)) {
                retained.add(snapshot);
                if (retainedBuckets.size() >= limit) {
                    return;
                }
            }
        }
    }

    private String latestSnapshotName(UUID islandId) {
        Path latest = islandRoot(islandId).resolve("latest");
        if (!Files.exists(latest)) {
            return "";
        }
        try {
            return Files.readString(latest, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            return "";
        }
    }

    private boolean manualSnapshot(Path snapshotDir) {
        Path manifest = snapshotDir.resolve("manifest.json");
        if (!Files.exists(manifest)) {
            return false;
        }
        try {
            String reason = IslandManifestJson.read(Files.readString(manifest, StandardCharsets.UTF_8)).snapshotReason();
            return reason != null && reason.toUpperCase(Locale.ROOT).contains("MANUAL");
        } catch (IOException exception) {
            return false;
        }
    }

    private String snapshotBucket(Path snapshotDir, String bucketType) {
        ZonedDateTime time = ZonedDateTime.ofInstant(snapshotTime(snapshotDir), ZoneOffset.UTC);
        return switch (bucketType) {
            case "hour" -> time.getYear() + "-" + time.getMonthValue() + "-" + time.getDayOfMonth() + "-" + time.getHour();
            case "day" -> time.toLocalDate().toString();
            case "week" -> time.getYear() + "-W" + time.get(WeekFields.ISO.weekOfWeekBasedYear());
            default -> snapshotDir.getFileName().toString();
        };
    }

    private Instant snapshotTime(Path snapshotDir) {
        Path manifest = snapshotDir.resolve("manifest.json");
        if (Files.exists(manifest)) {
            try {
                Instant savedAt = IslandManifestJson.read(Files.readString(manifest, StandardCharsets.UTF_8)).savedAt();
                if (savedAt != null) {
                    return savedAt;
                }
            } catch (RuntimeException | IOException ignored) {
                // Fall back to the directory timestamp below.
            }
        }
        try {
            return Files.getLastModifiedTime(snapshotDir).toInstant();
        } catch (IOException ignored) {
            return Instant.EPOCH;
        }
    }

    @Override
    public void deleteLiveState(UUID islandId) throws IOException {
        ensureRecovered();
        Path islandRoot = islandRoot(islandId);
        deleteFileAtomically(islandRoot.resolve("manifest.json"));
        deleteFileAtomically(islandRoot.resolve("latest"));
    }

    @Override
    public void deleteIsland(UUID islandId) throws IOException {
        ensureRecovered();
        deleteRecursively(islandRoot(islandId));
    }

    private synchronized void ensureRecovered() throws IOException {
        if (recovered) {
            return;
        }
        Files.createDirectories(root.resolve("islands"));
        recoverAbandonedWrites();
        recoverLiveManifests();
        recovered = true;
    }

    private void recoverAbandonedWrites() throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var stream = Files.walk(root)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                if (entry.equals(root)) {
                    continue;
                }
                String name = entry.getFileName().toString();
                if (name.startsWith(STAGING_PREFIX) || name.startsWith(OLD_PREFIX) || name.endsWith(TEMP_SUFFIX)) {
                    deleteRecursively(entry);
                }
            }
        }
    }

    private void recoverLiveManifests() throws IOException {
        Path islandsRoot = root.resolve("islands");
        if (!Files.exists(islandsRoot)) {
            return;
        }
        try (var stream = Files.list(islandsRoot)) {
            for (Path islandDir : stream.filter(Files::isDirectory).toList()) {
                Path latest = islandDir.resolve("latest");
                if (!Files.exists(latest)) {
                    continue;
                }
                String latestSnapshot = Files.readString(latest, StandardCharsets.UTF_8).trim();
                if (latestSnapshot.isBlank()) {
                    continue;
                }
                Path snapshotManifest = islandDir.resolve("snapshots").resolve(latestSnapshot).resolve("manifest.json");
                Path liveManifest = islandDir.resolve("manifest.json");
                if (Files.exists(snapshotManifest)) {
                    String snapshotManifestJson = Files.readString(snapshotManifest, StandardCharsets.UTF_8);
                    if (!Files.exists(liveManifest) || !Files.readString(liveManifest, StandardCharsets.UTF_8).equals(snapshotManifestJson)) {
                        atomicWriteString(liveManifest, snapshotManifestJson);
                    }
                }
            }
        }
    }

    private void publishLiveState(Path islandRoot, String latestSnapshot, String manifestJson) throws IOException {
        atomicWriteString(islandRoot.resolve("latest"), latestSnapshot);
        atomicWriteString(islandRoot.resolve("manifest.json"), manifestJson);
    }

    private ChecksumCopy writeStreamAtomically(InputStream input, Path target) throws IOException {
        if (input == null) {
            throw new IOException("missing bundle stream");
        }
        Files.createDirectories(target.getParent());
        Path temp = tempSibling(target);
        MessageDigest digest = sha256Digest();
        long size = 0L;
        byte[] buffer = new byte[64 * 1024];
        try (InputStream source = input; OutputStream output = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            int read;
            while ((read = source.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                size += read;
            }
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
        fsyncFile(temp);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        fsyncDirectory(target.getParent());
        return new ChecksumCopy(hex(digest.digest()), size);
    }

    private void copyFileAtomically(Path source, Path target) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            writeStreamAtomically(input, target);
        }
    }

    private void atomicWriteString(Path target, String value) throws IOException {
        writeBytesAtomically(target, value.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBytesAtomically(Path target, byte[] value) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = tempSibling(target);
        try (OutputStream output = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            output.write(value);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(temp);
            throw exception;
        }
        fsyncFile(temp);
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        fsyncDirectory(target.getParent());
    }

    private void publishDirectoryAtomically(Path stagingDir, Path targetDir) throws IOException {
        Path parent = targetDir.getParent();
        Files.createDirectories(parent);
        Path oldDir = null;
        if (Files.exists(targetDir)) {
            oldDir = parent.resolve(OLD_PREFIX + targetDir.getFileName() + "-" + UUID.randomUUID());
            Files.move(targetDir, oldDir, StandardCopyOption.ATOMIC_MOVE);
        }
        try {
            Files.move(stagingDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
            fsyncDirectory(parent);
            if (oldDir != null) {
                deleteRecursively(oldDir);
            }
        } catch (IOException | RuntimeException exception) {
            if (oldDir != null && !Files.exists(targetDir)) {
                try {
                    Files.move(oldDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
                    fsyncDirectory(parent);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw exception;
        }
    }

    private void validateSnapshotDirectory(Path directory, String bundleFileName, IslandBundleManifest expected) throws IOException {
        Path bundle = directory.resolve(bundleFileName);
        Path manifest = directory.resolve("manifest.json");
        Path checksums = directory.resolve("checksums.sha256");
        requireRegularFile(bundle, "bundle");
        requireRegularFile(manifest, "manifest");
        requireRegularFile(checksums, "checksum sidecar");
        IslandBundleManifest actualManifest = IslandManifestJson.read(Files.readString(manifest, StandardCharsets.UTF_8));
        long sizeBytes = Files.size(bundle);
        String checksum = checksumOf(bundle);
        if (!expected.checksum().equals(checksum) || !actualManifest.checksum().equals(checksum)) {
            throw new IOException("local bundle checksum validation failed");
        }
        if (expected.sizeBytes() != sizeBytes || actualManifest.sizeBytes() != sizeBytes) {
            throw new IOException("local bundle size validation failed");
        }
        if (!expected.storagePath().equals(actualManifest.storagePath())) {
            throw new IOException("local bundle manifest storage path validation failed");
        }
        String expectedChecksumLine = checksum + "  " + bundleFileName + "\n";
        if (!Files.readString(checksums, StandardCharsets.UTF_8).equals(expectedChecksumLine)) {
            throw new IOException("local checksum sidecar validation failed");
        }
    }

    private static void requireRegularFile(Path path, String label) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("missing local " + label + ": " + path);
        }
    }

    private static String checksumOf(Path path) throws IOException {
        MessageDigest digest = sha256Digest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(path)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (Exception exception) {
            throw new IOException("failed to create SHA-256 digest", exception);
        }
    }

    private static void fsyncFile(Path file) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void fsyncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static Path tempSibling(Path target) {
        return target.resolveSibling("." + target.getFileName() + "-" + UUID.randomUUID() + TEMP_SUFFIX);
    }

    private void deleteFileAtomically(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.delete(path);
        fsyncDirectory(path.getParent());
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private Path islandRoot(UUID islandId) {
        return root.resolve("islands").resolve(islandId.toString());
    }

    private String normalizedStoragePath(Path bundlePath) {
        return root.toAbsolutePath().normalize().relativize(bundlePath.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private Path resolveStoragePath(String storagePath) throws IOException {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IOException("missing storage path");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(storagePath).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IOException("storage path escapes root: " + storagePath);
        }
        return resolved;
    }

    private record ChecksumCopy(String checksum, long sizeBytes) {
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value & 0xff));
        }
        return builder.toString();
    }
}
