package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ExternalTarBundleExtractor implements BundleExtractor {
    @Override
    public ExtractedBundle extract(Path bundleFile, Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);
        validateBundleEntries(bundleFile);
        runTar(List.of("tar", "--zstd", "-xf", bundleFile.toAbsolutePath().toString(), "-C", targetDirectory.toAbsolutePath().toString()), "bundle extraction");
        Path manifest = targetDirectory.resolve("manifest.json");
        Path chunks = targetDirectory.resolve("chunks");
        if (!Files.isRegularFile(manifest)) {
            throw new IOException("extracted bundle is missing manifest.json");
        }
        if (!Files.isDirectory(chunks)) {
            throw new IOException("extracted bundle is missing chunks directory");
        }
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
