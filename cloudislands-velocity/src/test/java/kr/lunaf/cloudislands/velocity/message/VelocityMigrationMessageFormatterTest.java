package kr.lunaf.cloudislands.velocity.message;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VelocityMigrationMessageFormatterTest {
    private final VelocityMigrationMessageFormatter formatter = new VelocityMigrationMessageFormatter();

    @Test
    void reportsEmptyMigrationResponse() {
        assertEquals("Migration: no response", formatter.format(""));
    }

    @Test
    void reportsDisabledMigrationWithConfigContext() {
        String body = "{\"code\":\"MIGRATION_DISABLED\",\"sourcePlugin\":\"SuperiorSkyblock2\",\"migrationInputOnly\":true,\"runtimeDependency\":false,\"targetRuntime\":\"paper\"}";

        assertEquals(
            "SuperiorSkyblock2 migration is disabled by config. source=SuperiorSkyblock2 inputOnly=true runtimeDependency=false targetRuntime=paper",
            formatter.format(body)
        );
    }

    @Test
    void reportsFailedMigrationWithMessage() {
        assertEquals(
            "Migration: failed code=BAD_PATH message=missing export",
            formatter.format("{\"code\":\"BAD_PATH\",\"message\":\"missing export\"}")
        );
    }

    @Test
    void reportsPlanSummaryWithIssueSamples() {
        String body = "{"
            + "\"state\":\"PLANNED\","
            + "\"scanManifests\":3,"
            + "\"path\":\"/tmp/import\","
            + "\"manifestPath\":\"/tmp/manifest.json\","
            + "\"reportPath\":\"/tmp/report.json\","
            + "\"approvalToken\":\"abc\","
            + "\"sourcePlugin\":\"SuperiorSkyblock2\","
            + "\"migrationInputOnly\":true,"
            + "\"runtimeDependency\":false,"
            + "\"targetRuntime\":\"paper\","
            + "\"canImport\":true,"
            + "\"planManifests\":3,"
            + "\"rollbackPlanAvailable\":true,"
            + "\"approvalRequired\":true,"
            + "\"manifestStatus\":\"OK\","
            + "\"conflictStatus\":\"WARN\","
            + "\"conflictIssues\":1,"
            + "\"issues\":[{\"code\":\"MISSING_HOME\",\"blocking\":true},{\"code\":\"BAD_WARP\",\"blocking\":false}]"
            + "}";

        assertEquals(
            "Migration: state=PLANNED manifests=3 path=/tmp/import manifest=/tmp/manifest.json report=/tmp/report.json approval=abc source=SuperiorSkyblock2 inputOnly=true runtimeDependency=false targetRuntime=paper canImport=true planManifests=3 rollbackPlan=true approvalRequired=true manifestStatus=OK conflictStatus=WARN conflicts=1 issues=2 blocking=1 [MISSING_HOME(blocking), BAD_WARP]",
            formatter.format(body)
        );
    }
}
