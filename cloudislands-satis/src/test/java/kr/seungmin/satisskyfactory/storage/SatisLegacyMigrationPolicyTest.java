package kr.seungmin.satisskyfactory.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisLegacyMigrationPolicyTest {
    @Test
    void treatsSuperiorSkyblock2AndSatismcAsReadOnlyMigrationInputs() {
        assertEquals("M-LunaFarm/satismc", SatisLegacyMigrationPolicy.SOURCE_PROJECT);
        assertEquals("SuperiorSkyblock2", SatisLegacyMigrationPolicy.LEGACY_SKYBLOCK_SOURCE);
        assertEquals("satismc", SatisLegacyMigrationPolicy.LEGACY_SATIS_SOURCE);
        assertEquals("read-only-snapshot-or-sqlite-scan-no-live-provider-hooks", SatisLegacyMigrationPolicy.SOURCE_ACCESS_POLICY);
        assertEquals("legacy-provider-is-migration-input-only-never-runtime-dependency", SatisLegacyMigrationPolicy.RUNTIME_DEPENDENCY_POLICY);
        assertEquals("forbid-superiorskyblock2-runtime-hooks-after-import", SatisLegacyMigrationPolicy.RUNTIME_PROVIDER_HOOK_POLICY);
        assertEquals("verify-imported-satis-state-through-cloudislands-addon-state", SatisLegacyMigrationPolicy.ADDON_STATE_VERIFY_POLICY);
        assertEquals("admin-confirmation-required-before-import", SatisLegacyMigrationPolicy.APPROVAL_POLICY);
        assertEquals("CONFIRM_IMPORT", SatisLegacyMigrationPolicy.APPROVAL_TOKEN);
        assertEquals("CONFIRM_IMPORT:<dryrun-sha256>", SatisLegacyMigrationPolicy.FINGERPRINT_APPROVAL_TOKEN);
        assertEquals("plain-confirm-or-dryrun-sha256-bound-confirm", SatisLegacyMigrationPolicy.APPROVAL_TOKEN_POLICY);
        assertEquals("factory admin migration import <sqlitePath> CONFIRM_IMPORT|CONFIRM_IMPORT:<dryrun-sha256>", SatisLegacyMigrationPolicy.IMPORT_COMMAND);
        assertEquals("migrate-superiorskyblock2", SatisLegacyMigrationPolicy.LEGACY_COMMAND_ROOT);
        assertEquals("rollback-manifest-only-no-automatic-live-data-delete", SatisLegacyMigrationPolicy.ROLLBACK_POLICY);
        assertEquals("cloudislands-island-uuid", SatisLegacyMigrationPolicy.OUTPUT_ID_POLICY);
        assertEquals("create-cloudislands-migration-manifest-before-import", SatisLegacyMigrationPolicy.MANIFEST_POLICY);
        assertEquals("SuperiorSkyblock2,BentoBox,ASkyBlock,uSkyBlock,IridiumSkyblock", SatisLegacyMigrationPolicy.forbiddenRuntimeProvidersCsv());
    }

    @Test
    void coversRequiredLegacyAndSatisFields() {
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("island-id"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("owner-uuid"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("members"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("roles"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("permissions"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("island-location"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("island-size"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("home-location"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("warps"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("banned-visitors"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("level"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("worth"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("upgrades"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("flags"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("block-value-settings"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("satis-machines"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("satis-resource-nodes"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("satis-storage"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("satis-research"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("satis-market"));
        assertTrue(SatisLegacyMigrationPolicy.targetFieldRequired("satis-contracts"));
    }

    @Test
    void pinsMigrationPipelineAndAdminCommands() {
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("read-only-scan"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("create-migration-manifest"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("dry-run-validate"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("print-conflicts"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("admin-approve"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("db-import"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("extract-world-cell"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("create-island-bundle"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("verify-checksum"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("cloudislands-activate-test"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("verify-addon-state-roundtrip"));
        assertTrue(SatisLegacyMigrationPolicy.pipelineStepRequired("verify-no-legacy-provider-hook"));

        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration scan <sqlitePath>"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration dryrun <sqlitePath>"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration verify <sqlitePath>"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration verify-addon-state <islandUuid>"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration verify-no-legacy-provider"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration import <sqlitePath> CONFIRM_IMPORT|CONFIRM_IMPORT:<dryrun-sha256>"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migration rollback"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migrate-superiorskyblock2 verify-addon-state <islandUuid>"));
        assertTrue(SatisLegacyMigrationPolicy.adminCommands().contains("factory admin migrate-superiorskyblock2 verify-no-legacy-provider"));
    }

    @Test
    void policyCollectionsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> SatisLegacyMigrationPolicy.targetFields().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisLegacyMigrationPolicy.pipelineSteps().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisLegacyMigrationPolicy.adminCommands().add("legacy"));
    }
}
