package kr.lunaf.cloudislands.coreservice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationAdminServiceTest {
    @Test
    void superiorSkyblockImportSnapshotReasonIsMigrationBucketed() {
        assertEquals("BEFORE_MIGRATION:SUPERIORSKYBLOCK2_IMPORT", MigrationAdminService.MIGRATION_SNAPSHOT_REASON);
        assertTrue(MigrationAdminService.MIGRATION_SNAPSHOT_REASON.startsWith("BEFORE_MIGRATION"));
    }
}
