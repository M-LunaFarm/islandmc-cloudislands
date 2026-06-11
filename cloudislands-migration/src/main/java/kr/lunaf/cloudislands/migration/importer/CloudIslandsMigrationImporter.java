package kr.lunaf.cloudislands.migration.importer;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.superior.SuperiorSkyblock2DryRunValidator;

public final class CloudIslandsMigrationImporter {
    private final SuperiorSkyblock2DryRunValidator validator = new SuperiorSkyblock2DryRunValidator();

    public MigrationImportPlan dryRun(List<MigrationManifest> manifests) {
        return new MigrationImportPlan(List.copyOf(manifests), validator.validate(manifests));
    }

    public ImportResult importPlan(MigrationImportPlan plan) {
        if (!plan.canImport()) {
            return new ImportResult(false, 0, plan.issues());
        }
        List<MigrationIssue> warnings = new ArrayList<>();
        return new ImportResult(true, plan.manifests().size(), warnings);
    }

    public record ImportResult(boolean imported, int importedIslands, List<MigrationIssue> issues) {}
}
