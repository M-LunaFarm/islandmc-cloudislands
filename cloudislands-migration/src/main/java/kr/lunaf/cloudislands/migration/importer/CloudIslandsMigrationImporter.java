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
        return new MigrationImportPlan(manifests == null ? List.of() : List.copyOf(manifests), validator.validate(manifests));
    }

    public ImportResult importPlan(MigrationImportPlan plan, MigrationTarget target) {
        List<MigrationIssue> preflightIssues = preflightIssues(plan, target);
        if (plan == null) {
            return new ImportResult(false, 0, List.copyOf(preflightIssues), null);
        }
        if (!plan.report().canImport() || !preflightIssues.isEmpty()) {
            List<MigrationIssue> issues = new ArrayList<>(plan.issues());
            issues.addAll(preflightIssues);
            return new ImportResult(false, 0, List.copyOf(issues), null);
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
        return new ImportResult(issues.stream().noneMatch(MigrationIssue::blocking), imported, List.copyOf(issues), rollbackPlan);
    }

    public ImportResult importPlan(MigrationImportPlan plan) {
        return importPlan(plan, ignored -> {});
    }

    private List<MigrationIssue> preflightIssues(MigrationImportPlan plan, MigrationTarget target) {
        List<MigrationIssue> issues = new ArrayList<>();
        if (plan == null) {
            issues.add(new MigrationIssue("MIGRATION_PLAN_REQUIRED", "migration import requires a dry-run plan", true));
            return issues;
        }
        if (target == null) {
            issues.add(new MigrationIssue("MIGRATION_TARGET_REQUIRED", "migration import requires a target", true));
        }
        if (!plan.sourceFingerprintMatches()) {
            issues.add(new MigrationIssue("MIGRATION_SOURCE_CHANGED", "source fingerprint changed after dry-run; run dry-run again", true));
        }
        if (!plan.approved()) {
            issues.add(new MigrationIssue("MIGRATION_APPROVAL_REQUIRED", "approve import with token " + plan.requiredApprovalToken(), true));
        }
        return issues;
    }

    public interface MigrationTarget {
        void importIsland(MigrationManifest manifest);
    }

    public record ImportResult(boolean imported, int importedIslands, List<MigrationIssue> issues, MigrationRollbackPlan rollbackPlan) {}
}
