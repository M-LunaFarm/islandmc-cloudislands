package kr.lunaf.cloudislands.migration.importer;

import java.util.List;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.MigrationReport;
import kr.lunaf.cloudislands.migration.MigrationReportBuilder;

public record MigrationImportPlan(List<MigrationManifest> manifests, List<MigrationIssue> issues) {
    public boolean canImport() {
        return report().canImport();
    }

    public MigrationReport report() {
        return MigrationReportBuilder.build(manifests, issues);
    }
}
