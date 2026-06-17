package kr.lunaf.cloudislands.migration.importer;

import kr.lunaf.cloudislands.migration.MigrationManifest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudIslandsMigrationImporterTest {
    private static final UUID ISLAND_A = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID ISLAND_B = UUID.fromString("00000000-0000-0000-0000-000000001002");
    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-000000001101");
    private static final UUID OWNER_B = UUID.fromString("00000000-0000-0000-0000-000000001102");

    @Test
    void importRequiresDryRunApprovalTokenBeforeWritingTarget() {
        CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
        MigrationImportPlan plan = importer.dryRun(List.of(manifest(ISLAND_A, OWNER_A)));
        List<MigrationManifest> imported = new ArrayList<>();

        CloudIslandsMigrationImporter.ImportResult result = importer.importPlan(plan, imported::add);

        assertFalse(result.imported());
        assertEquals(0, result.importedIslands());
        assertTrue(imported.isEmpty());
        assertNull(result.rollbackPlan());
        assertEquals("MIGRATION_APPROVAL_REQUIRED", result.issues().get(0).code());
    }

    @Test
    void approvedImportWritesAllManifestsAndReturnsRollbackPlan() {
        CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
        MigrationImportPlan plan = importer.dryRun(List.of(manifest(ISLAND_A, OWNER_A), manifest(ISLAND_B, OWNER_B)));
        MigrationImportPlan approved = plan.approve(plan.requiredApprovalToken());
        List<UUID> imported = new ArrayList<>();

        CloudIslandsMigrationImporter.ImportResult result = importer.importPlan(approved, manifest -> imported.add(manifest.islandId()));

        assertTrue(result.imported());
        assertEquals(2, result.importedIslands());
        assertEquals(List.of(ISLAND_A, ISLAND_B), imported);
        assertEquals(List.of(ISLAND_A, ISLAND_B), result.rollbackPlan().importedIslandIds());
    }

    @Test
    void importRejectsMissingTargetWithoutWriting() {
        CloudIslandsMigrationImporter importer = new CloudIslandsMigrationImporter();
        MigrationImportPlan plan = importer.dryRun(List.of(manifest(ISLAND_A, OWNER_A)));
        MigrationImportPlan approved = plan.approve(plan.requiredApprovalToken());

        CloudIslandsMigrationImporter.ImportResult result = importer.importPlan(approved, null);

        assertFalse(result.imported());
        assertEquals(0, result.importedIslands());
        assertEquals("MIGRATION_TARGET_REQUIRED", result.issues().get(0).code());
        assertNull(result.rollbackPlan());
    }

    private static MigrationManifest manifest(UUID islandId, UUID ownerId) {
        return new MigrationManifest(
                islandId,
                ownerId,
                List.of(ownerId),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "minecraft:plains",
                "0",
                true,
                false,
                300,
                10L,
                "2500",
                "/superior/islands/" + islandId
        );
    }
}
