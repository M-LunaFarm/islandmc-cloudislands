package kr.lunaf.cloudislands.migration.importer;

import java.util.List;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;

public record MigrationImportPlan(List<MigrationManifest> manifests, List<MigrationIssue> issues) {
    public boolean canImport() {
        return issues.stream().noneMatch(MigrationIssue::blocking);
    }
}
