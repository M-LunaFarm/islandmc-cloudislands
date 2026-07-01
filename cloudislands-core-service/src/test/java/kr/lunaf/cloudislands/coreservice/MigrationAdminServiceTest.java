package kr.lunaf.cloudislands.coreservice;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationAdminServiceTest {
    @Test
    void superiorSkyblockImportSnapshotReasonIsMigrationBucketed() {
        assertEquals("BEFORE_MIGRATION:SUPERIORSKYBLOCK2_IMPORT", MigrationAdminService.MIGRATION_SNAPSHOT_REASON);
        assertTrue(MigrationAdminService.MIGRATION_SNAPSHOT_REASON.startsWith("BEFORE_MIGRATION"));
    }

    @Test
    void superiorSkyblockIslandChestImportsIntoCloudWarehouse() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/MigrationAdminBackend.java"));
        String service = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/MigrationAdminService.java"));
        String app = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/CloudIslandsCoreApplication.java"));

        assertTrue(backend.contains("IslandWarehouseRepository warehouse"));
        assertTrue(backend.contains("manifest.warehouseItems()"));
        assertTrue(backend.contains("warehouse.deposit(manifest.islandId(), item.materialKey(), item.amount())"));
        assertTrue(backend.contains("warehouseItemsMatch(manifest)"));
        assertTrue(service.contains("IslandWarehouseRepository warehouse"));
        assertTrue(app.contains("warehouseRepository,"));
    }
}
