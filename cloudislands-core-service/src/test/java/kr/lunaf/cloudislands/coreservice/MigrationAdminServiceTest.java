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

    @Test
    void migrationReportAndCompareAreFirstClassOperations() throws Exception {
        String backend = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/MigrationAdminBackend.java"));
        String service = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/MigrationAdminService.java"));
        String routes = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/SuperiorSkyblock2MigrationRoutes.java"));
        String client = Files.readString(Path.of("../cloudislands-core-client/src/main/java/kr/lunaf/cloudislands/coreclient/JdkMigrationCommandClient.java"));

        assertTrue(backend.contains("scan,status,dryrun,report,extract,approve,import,verify,compare,rollback-plan,rollback,unlock"));
        assertTrue(backend.contains("bank-balance,upgrades,missions,ratings,generators,limits,schematics,templates,stacked-blocks,custom-data,unsupported-data"), "migration target field contract must cover the full edit.md report surface");
        assertTrue(backend.contains("unsupportedFieldCount"), "migration JSON reports must expose unsupported field count");
        assertTrue(backend.contains("migrationDowntimeEstimatePolicy"), "migration reports must expose downtime planning evidence");
        assertTrue(backend.contains("migrationStackedCustomDataPolicy"), "migration reports must name stacked/custom/template/schematic coverage policy");
        assertTrue(backend.contains("migrationDataCategoryClassifications"), "migration reports must expose explicit data category classifications");
        assertTrue(backend.contains("boolean dryRunCanImport = lastPlan.report().canImport()"), "dry-run pass/fail must not require a prior approval token");
        assertTrue(backend.contains("lastApprovalToken = dryRunCanImport ? java.util.UUID.randomUUID().toString() : \"\""), "clean dry-runs must issue an operator approval token");
        assertTrue(backend.contains("owners=SUPPORTED"), "migration classifications must mark owner data supported");
        assertTrue(backend.contains("roles=PARTIAL"), "migration classifications must mark partially mapped role data");
        assertTrue(backend.contains("world-bundles=MANUAL"), "migration classifications must mark world bundle extraction as manual/operator-visible");
        assertTrue(backend.contains("ratings=UNSUPPORTED"), "migration classifications must mark unsupported rating data");
        assertTrue(backend.contains("unsafe-admin-actions=DANGEROUS"), "migration classifications must mark dangerous admin action parity explicitly");
        assertTrue(service.contains("MIGRATION_DATA_CATEGORY_CLASSIFICATIONS"), "migration service must expose the classification contract");
        assertTrue(backend.contains("public synchronized String report()"));
        assertTrue(backend.contains("public synchronized String approveLastPlan(String approvalToken)"));
        assertTrue(backend.contains("lastPlan = approvedPlan;"));
        assertTrue(backend.contains("public synchronized String compare(String islandKey)"));
        assertTrue(backend.contains("public synchronized String rollbackPlan()"));
        assertTrue(backend.contains("public synchronized String unlock(String confirmationToken)"));
        assertTrue(backend.contains("compareImportedManifest(MigrationManifest manifest)"));
        assertTrue(service.contains("backend.report()"));
        assertTrue(service.contains("backend.approveLastPlan(approvalToken)"));
        assertTrue(service.contains("backend.compare(islandKey)"));
        assertTrue(service.contains("backend.rollbackPlan()"));
        assertTrue(service.contains("backend.unlock(confirmationToken)"));
        assertTrue(routes.contains("/v1/admin/migrations/superiorskyblock2/report"));
        assertTrue(routes.contains("/v1/admin/migrations/superiorskyblock2/approve"));
        assertTrue(routes.contains("/v1/admin/migrations/superiorskyblock2/compare"));
        assertTrue(routes.contains("/v1/admin/migrations/superiorskyblock2/rollback-plan"));
        assertTrue(routes.contains("/v1/admin/migrations/superiorskyblock2/unlock"));
        assertTrue(client.contains("case \"report\""));
        assertTrue(client.contains("case \"approve\""));
        assertTrue(client.contains("case \"compare\""));
        assertTrue(client.contains("case \"rollback-plan\", \"rollbackplan\""));
        assertTrue(client.contains("case \"unlock\""));
    }
}
