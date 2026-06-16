package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.lunaf.cloudislands.storage.checksum.Sha256Checksums;

public final class ExternalTarBundleExtractor implements BundleExtractor {
    @Override
    public ExtractedBundle extract(Path bundleFile, Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);
        validateBundleEntries(bundleFile);
        runTar(List.of("tar", "--zstd", "-xf", bundleFile.toAbsolutePath().toString(), "-C", targetDirectory.toAbsolutePath().toString()), "bundle extraction");
        Path manifest = targetDirectory.resolve("manifest.json");
        Path chunks = targetDirectory.resolve("chunks");
        Path checksums = targetDirectory.resolve("checksums.sha256");
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("extracted bundle is missing manifest.json");
        }
        if (!Files.isDirectory(chunks)) {
            throw new IOException("extracted bundle is missing chunks directory");
        }
        if (!Files.isDirectory(targetDirectory.resolve("entities"))) {
            throw new IOException("extracted bundle is missing entities directory");
        }
        if (!Files.isDirectory(targetDirectory.resolve("block-entities"))) {
            throw new IOException("extracted bundle is missing block-entities directory");
        }
        if (!Files.isRegularFile(checksums)) {
            throw new IOException("extracted bundle is missing checksums.sha256");
        }
        verifyChecksums(targetDirectory, checksums);
        return new ExtractedBundle(targetDirectory, manifest, chunks);
    }

    private void validateBundleEntries(Path bundleFile) throws IOException {
        String output = runTar(List.of("tar", "--zstd", "-tf", bundleFile.toAbsolutePath().toString()), "bundle listing");
        for (String entry : output.split("\\R")) {
            if (unsafeEntry(entry)) {
                throw new IOException("unsafe bundle entry: " + entry);
            }
        }
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
                if (!relativeName.equals("checksums.sha256") && !listed.contains(relativeName)) {
                    unlisted.add(relativeName);
                }
            }
        }
        if (!unlisted.isEmpty()) {
            throw new IOException("bundle files missing checksums: " + String.join(",", unlisted));
        }
    }

    private String runTar(List<String> command, String label) throws IOException {
        ProcessBuilder processBuilder = new ProcessBuilder(new ArrayList<>(command));
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException(label + " failed with exit code " + exitCode + (output.isBlank() ? "" : ": " + output.trim()));
            }
            return output;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException(label + " interrupted", exception);
        }
    }
}
