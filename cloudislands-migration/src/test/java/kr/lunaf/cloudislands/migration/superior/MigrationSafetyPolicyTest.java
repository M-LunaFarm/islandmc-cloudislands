package kr.lunaf.cloudislands.migration.superior;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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
    void knownSkyblockProvidersAreForbiddenAtRuntime() {
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider("SuperiorSkyblock2"));
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider("bentobox"));
        assertTrue(MigrationSafetyPolicy.forbiddenRuntimeProvider(" ASkyBlock "));
        assertFalse(MigrationSafetyPolicy.forbiddenRuntimeProvider("CloudIslands"));
        assertEquals("SuperiorSkyblock2,BentoBox,ASkyBlock", MigrationSafetyPolicy.forbiddenRuntimeProvidersCsv());
    }

    @Test
    void boundaryMetadataPublishesRuntimeFence() {
        Map<String, String> metadata = MigrationSafetyPolicy.boundaryMetadata();

        assertEquals("SuperiorSkyblock2", metadata.get("sourcePlugin"));
        assertEquals("true", metadata.get("migrationInputOnly"));
        assertEquals("false", metadata.get("runtimeDependency"));
        assertEquals("CloudIslands", metadata.get("targetRuntime"));
        assertEquals("migration-input-only-no-runtime-hooks", metadata.get("runtimePolicy"));
        assertEquals("SuperiorSkyblock2,BentoBox,ASkyBlock", metadata.get("forbiddenRuntimeProviders"));
        assertEquals("warn-and-ignore-no-service-lookup-no-event-hooks-no-data-writes", metadata.get("forbiddenRuntimeAction"));
    }
}
