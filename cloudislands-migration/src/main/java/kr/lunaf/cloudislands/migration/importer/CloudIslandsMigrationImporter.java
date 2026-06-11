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

    public ImportResult importPlan(MigrationImportPlan plan, MigrationTarget target) {
        if (!plan.canImport()) {
            return new ImportResult(false, 0, plan.issues());
        }
        List<MigrationIssue> issues = new ArrayList<>();
        int imported = 0;
        for (MigrationManifest manifest : plan.manifests()) {
            try {
                target.importIsland(manifest);
                imported++;
            } catch (RuntimeException exception) {
                issues.add(new MigrationIssue("IMPORT_FAILED", manifest.islandId() + ": " + exception.getMessage(), true));
            }
        }
        return new ImportResult(issues.stream().noneMatch(MigrationIssue::blocking), imported, issues);
    }

    public ImportResult importPlan(MigrationImportPlan plan) {
        return importPlan(plan, ignored -> {});
    }

    public interface MigrationTarget {
        void importIsland(MigrationManifest manifest);
    }

    public record ImportResult(boolean imported, int importedIslands, List<MigrationIssue> issues) {}
}
