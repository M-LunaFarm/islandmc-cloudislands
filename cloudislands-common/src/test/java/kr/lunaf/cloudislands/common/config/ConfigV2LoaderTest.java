package kr.lunaf.cloudislands.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigV2LoaderTest {
    @Test
    void loadsOrderedImmutableSnapshotsAndRedactsEffectiveOutput() {
        ConfigSnapshot snapshot = ConfigV2Loader.load(List.of(
            new ConfigSource("profile", 20, """
                storage:
                  bucket: islands
                """),
            new ConfigSource("base", 10, """
                core-api:
                  auth-token: "${env:CORE_TOKEN}"
                """)
        ), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertTrue(snapshot.valid(), snapshot.validation().summary());
        assertEquals("base", snapshot.sources().get(0).name());
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), snapshot.createdAt());
        assertTrue(snapshot.effectiveYaml().contains("bucket: islands"));
        assertTrue(snapshot.redactedEffectiveYaml().contains("auth-token: <redacted>"));
    }

    @Test
    void computesRestartRequiredDiffsFromYamlPaths() {
        ConfigDiff diff = ConfigDiff.between("""
            node:
              id: island-1
            messages:
              locale: ko_kr
            """, """
            node:
              id: island-2
            messages:
              locale: en_us
            """, List.of("node.id"));

        assertTrue(diff.changed());
        assertTrue(diff.restartRequired());
        assertTrue(diff.changedLines().contains("node.id"));
        assertTrue(diff.changedLines().contains("messages.locale"));
    }

    @Test
    void migrationReportBlocksConflictingLegacyValues() {
        ConfigMigrationReport report = new ConfigMigrationReport(false, List.of("features.yml"), List.of(
            new ConfigIssue("MIGRATION_CONFLICT", "satis.market", "addons.cloudislands-satis.features.market != satis.features.market")
        ));

        assertFalse(report.canApply());
        assertEquals("blocked:MIGRATION_CONFLICT:satis.market", report.summary());
    }
}
