package kr.seungmin.satisskyfactory.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseServiceManifestPolicyTest {
    @Test
    void legacyImportManifestRecordsReadOnlyProviderBoundaries() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));

        assertTrue(source.contains("\"sourceAccessPolicy\""));
        assertTrue(source.contains("SatisLegacyMigrationPolicy.SOURCE_ACCESS_POLICY"));
        assertTrue(source.contains("\"legacyMutationPolicy\""));
        assertTrue(source.contains("SatisLegacyMigrationPolicy.LEGACY_MUTATION_POLICY"));
        assertTrue(source.contains("\"runtimeDependencyPolicy\""));
        assertTrue(source.contains("SatisLegacyMigrationPolicy.RUNTIME_DEPENDENCY_POLICY"));
        assertTrue(source.contains("\"runtimeProviderHookPolicy\""));
        assertTrue(source.contains("SatisLegacyMigrationPolicy.RUNTIME_PROVIDER_HOOK_POLICY"));
        assertTrue(source.contains("\"importProviderPrerequisite\""));
        assertTrue(source.contains("SatisLegacyMigrationPolicy.IMPORT_PROVIDER_PREREQUISITE"));
        assertTrue(source.contains("\"liveProviderHooks\": false"));
    }
}
