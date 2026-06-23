package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import kr.lunaf.cloudislands.common.storage.BundleIntegrityPolicy;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;

public final class ExternalTarBundleExtractor implements BundleExtractor {
    private static final Duration DEFAULT_PROCESS_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_PROCESS_OUTPUT_BYTES = 64 * 1024;
    private static final int MAX_ARCHIVE_ENTRIES = 1_000_000;
    private static final int MAX_ARCHIVE_PATH_BYTES = 4096;
    private static final long MAX_ARCHIVE_FILE_BYTES = 8L * 1024L * 1024L * 1024L;
    private static final long MAX_ARCHIVE_TOTAL_BYTES = 64L * 1024L * 1024L * 1024L;
    private static final long MAX_COMPRESSION_RATIO = 1_000L;

    private final Duration processTimeout;

    public ExternalTarBundleExtractor() {
        this(DEFAULT_PROCESS_TIMEOUT);
    }

    ExternalTarBundleExtractor(Duration processTimeout) {
        this.processTimeout = processTimeout == null || processTimeout.isNegative() || processTimeout.isZero()
            ? DEFAULT_PROCESS_TIMEOUT
            : processTimeout;
    }

    @Override
    public ExtractedBundle extract(Path bundleFile, Path targetDirectory) throws IOException {
        List<ArchiveEntry> entries = inspectBundle(bundleFile);
        validateArchiveEntries(bundleFile, entries);
        Path target = targetDirectory.toAbsolutePath().normalize();
        if (target.getParent() == null) {
            throw new IOException("refusing to extract bundle into unsafe target: " + targetDirectory);
        }
        Path staging = target.getParent().resolve(".extracting-" + target.getFileName() + "-" + UUID.randomUUID());
        try {
            Files.createDirectories(staging);
            runTar(List.of("tar", "--zstd", "-xf", bundleFile.toAbsolutePath().toString(), "-C", staging.toString()), "bundle extraction");
            validateExtractedTree(staging);
            Path manifest = staging.resolve(BundleIntegrityPolicy.MANIFEST_FILE);
            Path chunks = staging.resolve(BundleIntegrityPolicy.CHUNKS_DIRECTORY);
            Path checksums = staging.resolve(BundleIntegrityPolicy.CHECKSUM_FILE);
            requireRestoredShape(staging, manifest, chunks, checksums);
            verifyChecksums(staging, checksums);
            publishDirectoryAtomically(staging, target);
            return new ExtractedBundle(target, target.resolve(BundleIntegrityPolicy.MANIFEST_FILE), target.resolve(BundleIntegrityPolicy.CHUNKS_DIRECTORY));
        } catch (IOException | RuntimeException exception) {
            deleteRecursively(staging);
            throw exception;
        }
    }

    private void requireRestoredShape(Path root, Path manifest, Path chunks, Path checksums) throws IOException {
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("extracted bundle is missing " + BundleIntegrityPolicy.MANIFEST_FILE);
        }
        if (!Files.isDirectory(chunks, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted bundle is missing " + BundleIntegrityPolicy.CHUNKS_DIRECTORY + " directory");
        }
        if (!Files.isDirectory(root.resolve(BundleIntegrityPolicy.ENTITIES_DIRECTORY), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted bundle is missing " + BundleIntegrityPolicy.ENTITIES_DIRECTORY + " directory");
        }
        if (!Files.isDirectory(root.resolve(BundleIntegrityPolicy.BLOCK_ENTITIES_DIRECTORY), LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("extracted bundle is missing " + BundleIntegrityPolicy.BLOCK_ENTITIES_DIRECTORY + " directory");
        }
        if (!Files.isRegularFile(checksums)) {
            throw new IOException("extracted bundle is missing " + BundleIntegrityPolicy.CHECKSUM_FILE);
        }
    }

    private List<ArchiveEntry> inspectBundle(Path bundleFile) throws IOException {
        String output = runTar(List.of("tar", "--zstd", "-tvf", bundleFile.toAbsolutePath().toString()), "bundle listing");
        List<ArchiveEntry> entries = new ArrayList<>();
        for (String line : output.split("\\R")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            entries.add(parseArchiveEntry(line));
        }
        return entries;
    }

    ArchiveEntry parseArchiveEntry(String line) throws IOException {
        String[] parts = line.trim().split("\\s+", 6);
        if (parts.length < 6 || parts[0].isBlank()) {
            throw new IOException("unparseable bundle listing entry: " + line);
        }
        char type = parts[0].charAt(0);
        long size = 0L;
        if (type == '-' || type == '0') {
            try {
                size = Long.parseLong(parts[2]);
            } catch (NumberFormatException exception) {
                throw new IOException("unparseable bundle listing size: " + line, exception);
            }
        }
        String name = normalizeArchiveName(parts[5]);
        int linkMarker = name.indexOf(" -> ");
        if (linkMarker >= 0) {
            name = name.substring(0, linkMarker);
        }
        return new ArchiveEntry(name, type, size);
    }

    void validateArchiveEntries(Path bundleFile, List<ArchiveEntry> entries) throws IOException {
        if (entries == null || entries.isEmpty()) {
            throw new IOException("bundle archive is empty");
        }
        if (entries.size() > MAX_ARCHIVE_ENTRIES) {
            throw new IOException("bundle archive has too many entries: " + entries.size());
        }
        Set<String> requiredRootEntries = new HashSet<>();
        long totalSize = 0L;
        for (ArchiveEntry entry : entries) {
            if (entry == null || entry.name().isBlank()) {
                continue;
            }
            if (unsafeEntry(entry.name())) {
                throw new IOException("unsafe bundle entry: " + entry.name());
            }
            requirePathLength(entry.name(), "bundle archive entry");
            if (!allowedArchiveType(entry.type())) {
                throw new IOException("unsupported bundle archive entry type: " + entry.name());
            }
            if (entry.sizeBytes() < 0L || entry.sizeBytes() > MAX_ARCHIVE_FILE_BYTES) {
                throw new IOException("bundle archive entry exceeds file limit: " + entry.name());
            }
            totalSize = addSize(totalSize, entry.sizeBytes());
            String rootEntry = rootEntryName(entry.name());
            if (BundleIntegrityPolicy.requiredRootEntry(rootEntry)) {
                requiredRootEntries.add(rootEntry);
            }
        }
        if (totalSize > MAX_ARCHIVE_TOTAL_BYTES) {
            throw new IOException("bundle archive exceeds total size limit");
        }
        if (!requiredRootEntries.containsAll(BundleIntegrityPolicy.REQUIRED_ROOT_ENTRIES)) {
            throw new IOException("bundle archive is missing required root entries");
        }
        long compressedSize = Files.size(bundleFile);
        if (compressedSize > 0L && totalSize / compressedSize > MAX_COMPRESSION_RATIO) {
            throw new IOException("bundle archive compression ratio exceeds safety limit");
        }
    }

    private long addSize(long totalSize, long entrySize) throws IOException {
        try {
            return Math.addExact(totalSize, entrySize);
        } catch (ArithmeticException exception) {
            throw new IOException("bundle archive size overflow", exception);
        }
    }

    private boolean allowedArchiveType(char type) {
        return type == '-' || type == '0' || type == 'd';
    }

    private void validateExtractedTree(Path targetDirectory) throws IOException {
        Path root = targetDirectory.toAbsolutePath().normalize();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            int entries = 0;
            long totalSize = 0L;
            for (Path path : paths.toList()) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("extracted bundle has too many entries");
                }
                Path normalized = path.toAbsolutePath().normalize();
                if (!normalized.startsWith(root)) {
                    throw new IOException("extracted bundle entry escapes target: " + normalized);
                }
                requirePathLength(relativeName(root, normalized), "extracted bundle entry");
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in island bundles: " + relativeName(root, normalized));
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("unsupported island bundle entry type: " + relativeName(root, normalized));
                }
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    long size = Files.size(path);
                    if (size > MAX_ARCHIVE_FILE_BYTES) {
                        throw new IOException("extracted bundle entry exceeds file limit: " + relativeName(root, normalized));
                    }
                    totalSize = addSize(totalSize, size);
                    rejectHardLinkedOrSparse(path, root, normalized);
                }
            }
            if (totalSize > MAX_ARCHIVE_TOTAL_BYTES) {
                throw new IOException("extracted bundle exceeds total size limit");
            }
        }
    }

    private void rejectHardLinkedOrSparse(Path path, Path root, Path normalized) throws IOException {
        String relativeName = relativeName(root, normalized);
        try {
            Object links = Files.getAttribute(path, "unix:nlink", LinkOption.NOFOLLOW_LINKS);
            if (links instanceof Number number && number.longValue() > 1L) {
                throw new IOException("hard links are not allowed in island bundles: " + relativeName);
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems are still protected by archive entry type checks.
        }
        try {
            Object blocks = Files.getAttribute(path, "unix:blocks", LinkOption.NOFOLLOW_LINKS);
            if (blocks instanceof Number number) {
                long allocatedBytes = number.longValue() * 512L;
                long logicalBytes = Files.size(path);
                if (logicalBytes > 0L && allocatedBytes > 0L && allocatedBytes < logicalBytes) {
                    throw new IOException("sparse files are not allowed in island bundles: " + relativeName);
                }
            }
        } catch (UnsupportedOperationException ignored) {
            // Sparse detection is best-effort when unix attributes are unavailable.
        }
    }

    private String relativeName(Path root, Path normalized) {
        return root.equals(normalized) ? "." : root.relativize(normalized).toString().replace('\\', '/');
    }

    private boolean unsafeEntry(String entry) {
        if (entry == null || entry.isBlank()) {
            return false;
        }
        String value = entry.trim().replace('\\', '/');
        return value.startsWith("/")
            || value.startsWith("../")
            || value.equals("..")
            || value.contains("/../")
            || value.endsWith("/..")
            || value.matches("^[A-Za-z]:.*");
    }

    private String normalizeArchiveName(String entry) {
        if (entry == null) {
            return "";
        }
        String value = entry.trim().replace('\\', '/');
        while (value.startsWith("./")) {
            value = value.substring(2);
        }
        while (value.endsWith("/") && value.length() > 1) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String rootEntryName(String entry) {
        String value = normalizeArchiveName(entry);
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    private void verifyChecksums(Path root, Path checksumsFile) throws IOException {
        Set<String> listed = new HashSet<>();
        for (String line : Files.readAllLines(checksumsFile, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new IOException("invalid checksum line: " + line);
            }
            String expected = parts[0].trim();
            String relativeName = parts[1].trim().replace('\\', '/');
            if (unsafeEntry(relativeName)) {
                throw new IOException("unsafe checksum entry: " + relativeName);
            }
            requirePathLength(relativeName, "checksum entry");
            Path file = root.resolve(relativeName).normalize();
            if (!file.startsWith(root.toAbsolutePath().normalize()) && !file.startsWith(root.normalize())) {
                throw new IOException("checksum entry escapes bundle root: " + relativeName);
            }
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                throw new IOException("checksum entry is not a regular file: " + relativeName);
            }
            String actual;
            try (InputStream input = Files.newInputStream(file)) {
                actual = Sha256Checksums.of(input);
            }
            if (!expected.equalsIgnoreCase(actual)) {
                throw new IOException("bundle checksum mismatch: " + relativeName);
            }
            listed.add(relativeName);
        }
        List<String> unlisted = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("symbolic links are not allowed in island bundles: " + root.relativize(path));
                }
                String relativeName = root.relativize(path).toString().replace('\\', '/');
                if (BundleIntegrityPolicy.checksumProtectedFile(relativeName) && !listed.contains(relativeName)) {
                    unlisted.add(relativeName);
                }
            }
        }
        if (!unlisted.isEmpty()) {
            throw new IOException("bundle files missing checksums: " + String.join(",", unlisted));
        }
    }

    private void publishDirectoryAtomically(Path staging, Path target) throws IOException {
        Path parent = target.getParent();
        Files.createDirectories(parent);
        Path oldTarget = null;
        if (Files.exists(target)) {
            oldTarget = parent.resolve(".previous-" + target.getFileName() + "-" + UUID.randomUUID());
            Files.move(target, oldTarget, StandardCopyOption.ATOMIC_MOVE);
        }
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
            if (oldTarget != null) {
                deleteRecursively(oldTarget);
            }
        } catch (IOException | RuntimeException exception) {
            if (oldTarget != null && !Files.exists(target)) {
                try {
                    Files.move(oldTarget, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException restoreFailure) {
                    exception.addSuppressed(restoreFailure);
                }
            }
            throw exception;
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    String runTar(List<String> command, String label) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(new ArrayList<>(command));
        processBuilder.redirectErrorStream(true);
        ExecutorService outputReader = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cloudislands-tar-output-reader");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Process process = processBuilder.start();
            Future<String> output = outputReader.submit(() -> readProcessOutput(process.getInputStream()));
            long deadline = System.nanoTime() + processTimeout.toNanos();
            int exitCode;
            while (true) {
                if (output.isDone()) {
                    try {
                        output.get();
                    } catch (ExecutionException exception) {
                        process.destroyForcibly();
                        throw unwrapOutputFailure(label, exception);
                    }
                }
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    process.destroyForcibly();
                    output.cancel(true);
                    throw new IOException(label + " timed out after " + processTimeout.toSeconds() + "s");
                }
                if (process.waitFor(Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos), 100L), TimeUnit.MILLISECONDS)) {
                    exitCode = process.exitValue();
                    break;
                }
            }
            String processOutput;
            try {
                processOutput = output.get(1L, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException(label + " interrupted", exception);
            } catch (ExecutionException exception) {
                throw unwrapOutputFailure(label, exception);
            } catch (TimeoutException exception) {
                process.destroyForcibly();
                throw new IOException(label + " output reader timed out", exception);
            }
            if (exitCode != 0) {
                throw new IOException(label + " failed with exit code " + exitCode + (processOutput.isBlank() ? "" : ": " + processOutput.trim()));
            }
            return processOutput;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(label + " interrupted", exception);
        } finally {
            outputReader.shutdownNow();
        }
    }

    private IOException unwrapOutputFailure(String label, ExecutionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof IOException ioException) {
            return ioException;
        }
        return new IOException(label + " output reader failed", cause);
    }

    private String readProcessOutput(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (output.size() + read > MAX_PROCESS_OUTPUT_BYTES) {
                throw new IOException("tar output exceeded " + MAX_PROCESS_OUTPUT_BYTES + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toString(StandardCharsets.UTF_8);
    }

    private void requirePathLength(String path, String label) throws IOException {
        if (path != null && path.getBytes(StandardCharsets.UTF_8).length > MAX_ARCHIVE_PATH_BYTES) {
            throw new IOException(label + " path exceeds " + MAX_ARCHIVE_PATH_BYTES + " bytes: " + path.substring(0, Math.min(path.length(), 64)));
        }
    }

    record ArchiveEntry(String name, char type, long sizeBytes) {
    }
}
