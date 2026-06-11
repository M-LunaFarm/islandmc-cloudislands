package kr.lunaf.cloudislands.migration;

import java.nio.file.Path;
import java.util.List;

public final class SuperiorSkyblock2MigrationScanner {
    public ScanResult scan(Path superiorSkyblockDataPath) {
        return new ScanResult(List.of(), List.of());
    }

    public record ScanResult(List<MigrationManifest> manifests, List<MigrationIssue> issues) {}
}
