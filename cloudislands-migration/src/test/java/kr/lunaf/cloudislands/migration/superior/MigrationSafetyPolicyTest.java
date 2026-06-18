package kr.lunaf.cloudislands.migration.superior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.migration.importer.MigrationImportPlan;
import org.junit.jupiter.api.Test;

class MigrationSafetyPolicyTest {
    @Test
    void migrationSourceIsInputOnlyAndNeverRuntimeDependency() {
        assertEquals("SuperiorSkyblock2", MigrationSafetyPolicy.SOURCE_PLUGIN);
        assertEquals("CloudIslands", MigrationSafetyPolicy.TARGET_RUNTIME);
        assertTrue(MigrationSafetyPolicy.MIGRATION_INPUT_ONLY);
        assertFalse(MigrationSafetyPolicy.RUNTIME_DEPENDENCY_ALLOWED);
        assertEquals("migration-input-only-no-runtime-hooks", MigrationSafetyPolicy.RUNTIME_POLICY);
    }

    @Test
    void actionsSeparateReadOnlyPlanningFromWriteSteps() {
        assertTrue(MigrationSafetyPolicy.readOnly("scan"));
        assertTrue(MigrationSafetyPolicy.readOnly(" dry-run "));
        assertTrue(MigrationSafetyPolicy.readOnly("VERIFY"));
        assertTrue(MigrationSafetyPolicy.writeAction("extract"));
        assertTrue(MigrationSafetyPolicy.writeAction("import"));
        assertTrue(MigrationSafetyPolicy.writeAction("rollback"));
        assertTrue(MigrationSafetyPolicy.approvalRequired("import"));
        assertFalse(MigrationSafetyPolicy.approvalRequired("extract"));
    }

    @Test
    void migrationTargetsCoverSuperiorSkyblockDataNeededByCloudIslands() {
        assertEquals(
            List.of(
                "island-id",
                "owner-uuid",
                "members",
                "roles",
                "permissions",
                "island-location",
                "island-size",
                "homes",
                "warps",
                "banned-visitors",
                "level",
                "worth",
                "upgrades",
                "flags",
                "block-value-settings"
            ),
            MigrationSafetyPolicy.REQUIRED_TARGET_FIELDS
        );
        assertTrue(MigrationSafetyPolicy.requiredTargetField("owner-uuid"));
        assertTrue(MigrationSafetyPolicy.requiredTargetField(" block-value-settings "));
        assertFalse(MigrationSafetyPolicy.requiredTargetField("runtime-hook"));
    }

    @Test
    void migrationPipelineRequiresValidationApprovalImportBundleVerifyAndActivateTest() {
        assertEquals(
            List.of(
                "read-only-scan",
                "cloudislands-migration-manifest",
                "dry-run-validation",
                "conflict-report",
                "admin-approval",
                "db-import",
                "world-cell-extract",
                "island-bundle-create",
                "checksum-verify",
                "cloudislands-activate-test"
            ),
            MigrationSafetyPolicy.REQUIRED_PIPELINE_STEPS
        );
        assertTrue(MigrationSafetyPolicy.requiredPipelineStep("read-only-scan"));
        assertTrue(MigrationSafetyPolicy.requiredPipelineStep("checksum-verify"));
        assertTrue(MigrationSafetyPolicy.requiredPipelineStep("cloudislands-activate-test"));
        assertFalse(MigrationSafetyPolicy.requiredPipelineStep("live-superiorskyblock2-write"));
    }

    @Test
    void adminCommandSurfaceMatchesTheMigrationToolContract() {
        assertEquals(
            List.of(
                "/ciadmin migrate-superiorskyblock2 scan",
                "/ciadmin migrate-superiorskyblock2 dryrun",
                "/ciadmin migrate-superiorskyblock2 import",
                "/ciadmin migrate-superiorskyblock2 verify",
                "/ciadmin migrate-superiorskyblock2 rollback"
            ),
            MigrationSafetyPolicy.REQUIRED_ADMIN_COMMANDS
        );
        assertTrue(MigrationSafetyPolicy.requiredAdminCommand("/ciadmin migrate-superiorskyblock2 scan"));
        assertTrue(MigrationSafetyPolicy.requiredAdminCommand(" /ciadmin migrate-superiorskyblock2 verify "));
        assertFalse(MigrationSafetyPolicy.requiredAdminCommand("/ciadmin migrate-superiorskyblock2 mutate-live"));
    }

    @Test
    void knownSkyblockProvidersAreForbiddenAtRuntime() {
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider("SuperiorSkyblock2"));
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider("bentobox"));
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider(" ASkyBlock "));
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider("uSkyBlock"));
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider("IridiumSkyblock"));
        assertFalse(MigrationSafetyPolicy.forbiddenRuntimeProvider("CloudIslands"));
        assertEquals("SuperiorSkyblock2,BentoBox,ASkyBlock,uSkyBlock,IridiumSkyblock", MigrationSafetyPolicy.forbiddenRuntimeProvidersCsv());
    }

    @Test
    void importPreflightRequiresCleanDryRunApprovalAndStableSourceFingerprint() {
        assertEquals(
            "import-runs-only-after-clean-dryrun-admin-approval-and-unchanged-source-fingerprint",
            MigrationSafetyPolicy.IMPORT_PREFLIGHT_POLICY
        );
        assertEquals(
            List.of("dry-run-report-can-import", "admin-approval-token-present", "source-fingerprint-unchanged"),
            MigrationSafetyPolicy.IMPORT_PREFLIGHT_REQUIREMENTS
        );
        assertTrue(MigrationSafetyPolicy.importPreflightRequirement("dry-run-report-can-import"));
        assertTrue(MigrationSafetyPolicy.importPreflightSatisfied(true, true, true));
        assertFalse(MigrationSafetyPolicy.importPreflightSatisfied(false, true, true));
        assertFalse(MigrationSafetyPolicy.importPreflightSatisfied(true, false, true));
        assertFalse(MigrationSafetyPolicy.importPreflightSatisfied(true, true, false));

        MigrationImportPlan plan = new MigrationImportPlan(List.of(), List.of());
        assertFalse(plan.importPreflightSatisfied());
        assertTrue(plan.approve(plan.requiredApprovalToken()).importPreflightSatisfied());
    }

    @Test
    void boundaryMetadataPublishesRuntimeFence() {
        Map<String, String> metadata = MigrationSafetyPolicy.boundaryMetadata();

        assertEquals("SuperiorSkyblock2", metadata.get("sourcePlugin"));
        assertEquals("true", metadata.get("migrationInputOnly"));
        assertEquals("false", metadata.get("runtimeDependency"));
        assertEquals("CloudIslands", metadata.get("targetRuntime"));
        assertEquals("migration-input-only-no-runtime-hooks", metadata.get("runtimePolicy"));
        assertEquals("SuperiorSkyblock2,BentoBox,ASkyBlock,uSkyBlock,IridiumSkyblock", metadata.get("forbiddenRuntimeProviders"));
        assertEquals("warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes", metadata.get("forbiddenRuntimeAction"));
        assertEquals("import-runs-only-after-clean-dryrun-admin-approval-and-unchanged-source-fingerprint", metadata.get("importPreflightPolicy"));
    }
}
