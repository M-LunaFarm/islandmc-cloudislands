package kr.lunaf.cloudislands.migration.importer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.migration.MigrationIssue;
import kr.lunaf.cloudislands.migration.MigrationManifest;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackPlan;
import kr.lunaf.cloudislands.migration.superior.SuperiorSkyblock2DryRunValidator;

public final class CloudIslandsMigrationImporter {
    private final SuperiorSkyblock2DryRunValidator validator = new SuperiorSkyblock2DryRunValidator();

    public MigrationImportPlan dryRun(List<MigrationManifest> manifests) {
        return new MigrationImportPlan(List.copyOf(manifests), validator.validate(manifests));
    }

    public ImportResult importPlan(MigrationImportPlan plan, MigrationTarget target) {
        if (!plan.canImport()) {
            return new ImportResult(false, 0, plan.issues(), null);
        }
        List<MigrationIssue> issues = new ArrayList<>();
        List<UUID> importedIds = new ArrayList<>();
        int imported = 0;
        for (MigrationManifest manifest : plan.manifests()) {
            try {
                target.importIsland(manifest);
                importedIds.add(manifest.islandId());
                imported++;
            } catch (RuntimeException exception) {
                issues.add(new MigrationIssue("IMPORT_FAILED", manifest.islandId() + ": " + exception.getMessage(), true));
            }
        }
        MigrationRollbackPlan rollbackPlan = new MigrationRollbackPlan(UUID.randomUUID(), List.copyOf(importedIds), Instant.now());
        return new ImportResult(issues.stream().noneMatch(MigrationIssue::blocking), imported, issues, rollbackPlan);
    }

    public ImportResult importPlan(MigrationImportPlan plan) {
        return importPlan(plan, ignored -> {});
    }

    public interface MigrationTarget {
        void importIsland(MigrationManifest manifest);
    }

    public record ImportResult(boolean imported, int importedIslands, List<MigrationIssue> issues, MigrationRollbackPlan rollbackPlan) {}
}
