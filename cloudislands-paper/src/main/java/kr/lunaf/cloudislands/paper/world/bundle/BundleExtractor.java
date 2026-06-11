package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.IOException;
import java.nio.file.Path;

public interface BundleExtractor {
    ExtractedBundle extract(Path bundleFile, Path targetDirectory) throws IOException;

    record ExtractedBundle(Path rootDirectory, Path manifestFile, Path chunksDirectory) {}
}
