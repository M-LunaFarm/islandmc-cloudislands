package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExternalTarBundleExtractor implements BundleExtractor {
    @Override
    public ExtractedBundle extract(Path bundleFile, Path targetDirectory) throws IOException {
        Files.createDirectories(targetDirectory);
        ProcessBuilder processBuilder = new ProcessBuilder("tar", "--zstd", "-xf", bundleFile.toAbsolutePath().toString(), "-C", targetDirectory.toAbsolutePath().toString());
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("bundle extraction failed with exit code " + exitCode);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("bundle extraction interrupted", exception);
        }
        return new ExtractedBundle(targetDirectory, targetDirectory.resolve("manifest.json"), targetDirectory.resolve("chunks"));
    }
}
