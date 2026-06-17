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
        assertEquals("true", metadata.get("addon-removal-safe"));
        assertEquals("preserve-addon-state-by-island-uuid", metadata.get("addon-data-retention"));
        assertEquals("false", metadata.get("addon-runtime-owns-islands"));
        assertEquals("CORE_API", metadata.get("addon-default-database-mode"));
        assertEquals("false", metadata.get("superior-runtime-dependency"));
        assertEquals("true", metadata.get("cloudislands-api-only"));
        assertEquals("true", metadata.get("config-gated"));
        assertEquals("cloudislands-api-required-no-standalone-island-runtime", metadata.get("cloudislands-required-policy"));
        assertEquals("bootstrap-or-services-manager", metadata.get("cloudislands-api-resolution"));
        assertEquals("CloudIslands", metadata.get("runtime-hard-depend-plugin"));
        assertEquals("false", metadata.get("standalone-island-management"));
        assertEquals("disable-plugin-clear-features-register-no-components", metadata.get("missing-cloudislands-behavior"));
    }
}
