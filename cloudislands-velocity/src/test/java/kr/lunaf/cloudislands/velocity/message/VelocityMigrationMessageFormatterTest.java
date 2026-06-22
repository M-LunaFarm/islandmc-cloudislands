package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import org.junit.jupiter.api.Test;

class VelocityMigrationMessageFormatterTest {
    private final VelocityMigrationMessageFormatter formatter = new VelocityMigrationMessageFormatter();

    @Test
    void reportsEmptyMigrationResponse() {
        assertEquals("Migration: no response", formatter.format(null));
    }

    @Test
    void reportsDisabledMigration() {
        MigrationRunSnapshot snapshot = failure("MIGRATION_DISABLED", "disabled by config");

        assertEquals(
            "SuperiorSkyblock2 migration is disabled by config.",
            formatter.format(snapshot)
        );
    }

    @Test
    void reportsFailedMigrationWithMessage() {
        assertEquals(
            "Migration: failed code=BAD_PATH message=missing export",
            formatter.format(failure("BAD_PATH", "missing export"))
        );
    }

    @Test
    void reportsPlanSummaryWithIssueSamples() {
        MigrationRunSnapshot snapshot = new MigrationRunSnapshot(
            "PLANNED",
            "/tmp/import",
            "/tmp/manifest.json",
            "/tmp/report.json",
            "abc",
            "fingerprint",
            3,
            true,
            false,
            0,
            false,
            0,
            false,
            0,
            true,
            false,
            0,
            0L,
            0L,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            0,
            1,
            1,
            List.of(new MigrationIssueSnapshot("MISSING_HOME", "", true), new MigrationIssueSnapshot("BAD_WARP", "", false))
        );

        assertEquals(
            "Migration: state=PLANNED manifests=3 path=/tmp/import manifest=/tmp/manifest.json report=/tmp/report.json approval=abc canImport=true rollbackPlan=true imported=false islands=0 passed=false expected=0 activationTested=0 activationPassed=0 rolledBack=false removed=0 extracted=0 files=0 bytes=0 blocking=1 warnings=1 issues=2 blocking=1 [MISSING_HOME(blocking), BAD_WARP]",
            formatter.format(snapshot)
        );
    }

    private static MigrationRunSnapshot failure(String code, String message) {
        return new MigrationRunSnapshot(
            code,
            "",
            0,
            false,
            false,
            0,
            false,
            0,
            false,
            0,
            List.of(new MigrationIssueSnapshot(code, message, true))
        );
    }
}
