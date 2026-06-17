package kr.lunaf.cloudislands.migration.world;

import java.util.List;

public record MigrationWorldExtractionReport(
    int bundles,
    int runtimeRestoreCompatibleBundles,
    long files,
    long sizeBytes,
    String compressionFormats,
    String restoreCompatibilityStatus,
    List<MigrationWorldBundle> entries
) {
    public static MigrationWorldExtractionReport of(List<MigrationWorldBundle> bundles) {
        List<MigrationWorldBundle> safeBundles = bundles == null ? List.of() : List.copyOf(bundles);
        long files = 0L;
        long sizeBytes = 0L;
        int compatible = 0;
        StringBuilder formats = new StringBuilder();
        for (MigrationWorldBundle bundle : safeBundles) {
            if (bundle == null) {
                continue;
            }
            files += bundle.fileCount();
            sizeBytes += bundle.sizeBytes();
            if (bundle.runtimeRestoreCompatible()) {
                compatible++;
            }
            String compression = bundle.compression() == null || bundle.compression().isBlank() ? "unknown" : bundle.compression();
            if (formats.indexOf(compression) < 0) {
                if (!formats.isEmpty()) {
                    formats.append(',');
                }
                formats.append(compression);
            }
        }
        return new MigrationWorldExtractionReport(
            safeBundles.size(),
            compatible,
            files,
            sizeBytes,
            formats.isEmpty() ? "none" : formats.toString(),
            compatible == safeBundles.size() ? "runtime-restore-ready" : "migration-import-bundle-conversion-required",
            safeBundles
        );
    }

    public boolean fullyRuntimeRestoreCompatible() {
        return bundles > 0 && bundles == runtimeRestoreCompatibleBundles;
    }
}
