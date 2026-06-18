package kr.seungmin.satisskyfactory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SatisSkyFactoryPluginTest {
    @Test
    void dirtySavePeriodUsesDatabaseSaveSecondsFirst() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.save-interval-seconds", 60);
        config.set("settings.dirty-save-period-ticks", 200);

        assertEquals(1200, SatisSkyFactoryPlugin.dirtySavePeriodTicks(config));
    }

    @Test
    void dirtySavePeriodFallsBackToLegacyTickSettings() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("settings.dirty-save-period-ticks", 200);

        assertEquals(200, SatisSkyFactoryPlugin.dirtySavePeriodTicks(config));
    }

    @Test
    void activeParticleLimitFollowsVisualsToggleAndCapsPerTick() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("visuals.particles", true);

        assertEquals(64, SatisSkyFactoryPlugin.activeParticleLimit(config, 300));

        config.set("visuals.particles", false);
        assertEquals(0, SatisSkyFactoryPlugin.activeParticleLimit(config, 300));
    }

    @Test
    void integrationMetadataDocumentsCloudIslandsAddonMigration() {
        Map<String, String> metadata = SatisSkyFactoryPlugin.cloudIslandsIntegrationMetadata();

        assertEquals("satismc", metadata.get("origin-project"));
        assertEquals("cloudislands-addon.yml", metadata.get("addon-descriptor-resource"));
        assertEquals("cloudislands-addon-yaml", metadata.get("addon-descriptor-format"));
        assertEquals("external-plugin", metadata.get("addon-packaging"));
        assertEquals("external-plugin,built-in-feature-pack,built-in-compatible", metadata.get("addon-supported-packaging"));
        assertEquals("same-cloudislands-addon-spi-for-external-plugin-and-built-in-feature-pack", metadata.get("addon-spi-policy"));
        assertEquals("true", metadata.get("addon-removal-safe"));
        assertEquals("missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore", metadata.get("addon-removal-policy"));
        assertEquals("reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid", metadata.get("addon-reconnect-policy"));
        assertEquals("preserve-addon-state-by-island-uuid", metadata.get("addon-data-retention"));
        assertEquals("false", metadata.get("addon-runtime-owns-islands"));
        assertEquals("CORE_API", metadata.get("addon-default-database-mode"));
        assertEquals("false", metadata.get("superior-runtime-dependency"));
        assertEquals("true", metadata.get("cloudislands-api-only"));
        assertEquals("true", metadata.get("config-gated"));
        assertEquals("setup.satis.mode,addons.cloudislands-satis.integration.mode,integration.mode", metadata.get("integration-mode-config-paths"));
        assertEquals("cloudislands-api-required-no-standalone-island-runtime", metadata.get("cloudislands-required-policy"));
        assertEquals("bootstrap-or-services-manager", metadata.get("cloudislands-api-resolution"));
        assertEquals("CloudIslands", metadata.get("runtime-hard-depend-plugin"));
        assertEquals("false", metadata.get("standalone-island-management"));
        assertEquals("disable-plugin-clear-features-register-no-components", metadata.get("missing-cloudislands-behavior"));
        assertEquals("read-only-snapshot-or-sqlite-scan-no-live-provider-hooks", metadata.get("migration-source-policy"));
        assertEquals("legacy-provider-is-migration-input-only-never-runtime-dependency", metadata.get("migration-runtime-dependency-policy"));
        assertEquals("create-cloudislands-migration-manifest-before-import", metadata.get("migration-manifest-policy"));
        assertEquals("cloudislands-island-uuid", metadata.get("migration-output-id-policy"));
    }
}
