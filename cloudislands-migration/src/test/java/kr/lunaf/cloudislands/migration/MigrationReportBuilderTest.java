package kr.lunaf.cloudislands.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MigrationReportBuilderTest {
    private static final UUID ISLAND_A = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID OWNER_A = UUID.fromString("00000000-0000-0000-0000-000000000902");

    @Test
    void reportExposesMigrationRiskDiagnostics() {
        MigrationReport report = MigrationReportBuilder.build(
            List.of(manifestWithoutHomeOrWarp()),
            List.of(
                new MigrationIssue("OWNER_NOT_FOUND", "missing owner", true),
                new MigrationIssue("WORLD_SOURCE_NOT_FOUND", "missing source world", true),
                new MigrationIssue("PERMISSIONS_MISMATCH", "permissions differ after import", true),
                new MigrationIssue("UNKNOWN_FLAG", "unknown flag", false),
                new MigrationIssue("INVALID_BLOCK_VALUE", "invalid block value", true),
                new MigrationIssue("INVALID_BANK_BALANCE", "invalid bank", true),
                new MigrationIssue("WORLD_CHECKSUM_FAILED", "world checksum failed", true),
                new MigrationIssue("HOMES_MISMATCH", "homes differ after import", true),
                new MigrationIssue("WARPS_MISMATCH", "warps differ after import", true),
                new MigrationIssue("UNSUPPORTED_FIELD", "unsupported SuperiorSkyblock2 field islandBorderColor", false)
            )
        );

        assertEquals(1, report.totalIslands());
        assertEquals(0, report.importableIslandCount());
        assertEquals(1, report.ownerMissingCount());
        assertEquals(1, report.worldPathMissingCount());
        assertEquals(1, report.homeMissingCount());
        assertEquals(1, report.warpMissingCount());
        assertEquals(1, report.homeConversionFailureCount());
        assertEquals(1, report.warpConversionFailureCount());
        assertEquals(1, report.permissionConversionFailureCount());
        assertEquals(1, report.unknownFlagCount());
        assertEquals(1, report.blockValueConversionFailureCount());
        assertEquals(1, report.bankEconomyConversionFailureCount());
        assertEquals(1, report.worldBundleChecksumFailureCount());
        assertEquals(3, report.cloudIslandsPostImportDifferenceCount());
        assertEquals(1, report.unsupportedFieldCount());
        assertTrue(report.rollbackPossible());
    }

    @Test
    void reportCountsImportableIslandsOnlyWhenDryRunHasNoBlockingIssues() {
        MigrationReport report = MigrationReportBuilder.build(List.of(manifestWithoutHomeOrWarp()), List.of());

        assertEquals(1, report.importableIslandCount());
    }

    @Test
    void rollbackIsNotPossibleWhenRollbackItselfIsBlocked() {
        MigrationReport report = MigrationReportBuilder.build(
            List.of(manifestWithoutHomeOrWarp()),
            List.of(new MigrationIssue("ROLLBACK_TARGET_REQUIRED", "missing rollback target", true))
        );

        assertFalse(report.rollbackPossible());
    }

    private static MigrationManifest manifestWithoutHomeOrWarp() {
        return new MigrationManifest(
            ISLAND_A,
            OWNER_A,
            List.of(OWNER_A),
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
            "1000",
            true,
            false,
            300,
            120L,
            "125000",
            "/superior/islands/" + ISLAND_A
        );
    }
}
