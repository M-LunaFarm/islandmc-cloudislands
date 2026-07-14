package kr.lunaf.cloudislands.paper.world.bundle;

import java.io.IOException;
import java.nio.file.Path;

public interface BundleExtractor {
    ExtractedBundle extract(Path bundleFile, Path targetDirectory) throws IOException;

    record ExtractedBundle(Path rootDirectory, Path manifestFile, Path chunksDirectory, Path entitiesDirectory, Path poiDirectory) {
        public ExtractedBundle(Path rootDirectory, Path manifestFile, Path chunksDirectory) {
            this(rootDirectory, manifestFile, chunksDirectory, rootDirectory.resolve("entities"), rootDirectory.resolve("poi"));
        }
    }
}
