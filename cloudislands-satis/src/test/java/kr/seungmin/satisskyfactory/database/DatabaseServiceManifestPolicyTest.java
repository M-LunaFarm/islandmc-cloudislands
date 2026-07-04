package kr.seungmin.satisskyfactory.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseServiceManifestPolicyTest {
    @Test
    void legacyImportManifestSurfaceIsRemoved() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/database/DatabaseService.java"));

        assertFalse(source.contains("SatisLegacyMigrationPolicy"));
        assertFalse(source.contains("LegacyImport"));
        assertFalse(source.contains("scanLegacyDatabase"));
        assertFalse(source.contains("importLegacyDatabase"));
        assertFalse(source.contains("rollbackLastLegacyImport"));
        assertTrue(source.contains("SatisSchemaService"));
    }
}
