package kr.seungmin.satisskyfactory;

import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("cloudislands-api-required-no-standalone-island-runtime", metadata.get("integration-mode-runtime-boundary"));
        assertEquals("cloudislands-api-required-no-standalone-island-runtime", metadata.get("cloudislands-required-policy"));
        assertEquals("bootstrap-or-services-manager", metadata.get("cloudislands-api-resolution"));
        assertEquals("CloudIslands", metadata.get("runtime-hard-depend-plugin"));
        assertEquals("false", metadata.get("standalone-island-management"));
        assertEquals("cloudislands-api-required-no-standalone-island-runtime", metadata.get("standalone-island-runtime-policy"));
        assertEquals("disabled-no-standalone-island-management", metadata.get("island-runtime-authority"));
        assertEquals("core-api-requires-cloudislands-api-addon-state-and-hydrated-island", metadata.get("runtime-tick-authority-policy"));
        assertEquals("local-sqlite-fallback-preserves-state-but-blocks-distributed-runtime-ticks", metadata.get("runtime-tick-authority-local-fallback-policy"));
        assertEquals("core-api-writes-require-addon-state-write-authority", metadata.get("runtime-write-authority-policy"));
        assertEquals("local-sqlite-fallback-preserves-state-but-blocks-distributed-runtime-writes", metadata.get("runtime-write-authority-local-fallback-policy"));
        assertEquals("disable-plugin-clear-features-register-no-components", metadata.get("missing-cloudislands-behavior"));
        assertEquals("no-hardcoded-island-node-count", metadata.get("island-state-node-count-policy"));
        assertEquals("node-id-is-routing-context-not-addon-state-key", metadata.get("island-state-node-identity-policy"));
        assertEquals("five-or-six-island-nodes-are-supported-when-each-node-has-unique-node-id-unique-velocity-server-name-shared-storage-and-route-candidate-readiness", metadata.get("island-state-five-six-node-policy"));
        assertEquals("seven-or-more-island-nodes-use-the-same-live-route-candidate-rules-with-no-player-command-change", metadata.get("island-state-seven-plus-node-policy"));
        assertEquals("node-count-does-not-change-satis-state-keys-or-storage-authority", metadata.get("island-state-scale-policy"));
        assertEquals("A-node-save-B-node-restore-by-island-uuid", metadata.get("island-state-node-handoff-policy"));
        assertEquals("read-only-snapshot-or-sqlite-scan-no-live-provider-hooks", metadata.get("migration-source-policy"));
        assertEquals("legacy-provider-is-migration-input-only-never-runtime-dependency", metadata.get("migration-runtime-dependency-policy"));
        assertEquals("verify-no-legacy-provider-passed-before-import", metadata.get("legacy-satismc-import-provider-prerequisite"));
        assertEquals("create-cloudislands-migration-manifest-before-import", metadata.get("migration-manifest-policy"));
        assertEquals("cloudislands-island-uuid", metadata.get("migration-output-id-policy"));
        assertEquals(
                String.join(",", AddonStateBulkSaveRequest.GLOBAL_ENDPOINTS) + "," + String.join(",", AddonStateBulkLoadRequest.GLOBAL_ENDPOINTS),
                metadata.get("database-core-api-endpoint")
        );
        assertEquals(
                String.join(",", AddonStateBulkSaveRequest.ISLAND_ENDPOINTS) + "," + String.join(",", AddonStateBulkLoadRequest.ISLAND_ENDPOINTS),
                metadata.get("database-core-api-island-endpoint")
        );
    }

    @Test
    void runtimePlanUsesCloudIslandsApiPresenceForStandaloneGuard() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("operationalFeatureEnabled(\"addon-state\"),\n                cloudIslandsApi != null"));
    }

    @Test
    void commandsFeatureGateUnregistersFactoryEntrypoints() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("if (!operationalFeatureEnabled(\"commands\"))"));
        assertTrue(source.contains("unregisterAddonCommands();"));
        assertTrue(source.contains("disabled-feature-unregisters-factory-and-sfactory-commands-and-registers-no-active-satis-command"));
        assertTrue(source.contains("commands-feature-disabled-unregisters-command-list-entrypoints"));
    }

    @Test
    void placeholderFeatureGateUnregistersExpansionWhenDisabled() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));

        assertTrue(source.contains("private void registerPlaceholders()"));
        assertTrue(source.contains("if (!placeholderRuntimeEnabled())"));
        assertTrue(source.contains("placeholderHook.unregister();"));
        assertTrue(source.contains("placeholderHook = null;"));
        assertTrue(source.contains("private boolean placeholderRuntimeEnabled()"));
        assertTrue(source.contains("return operationalFeatureEnabled(\"placeholders\")\n                && operationalFeatureEnabled(\"machines\")\n                && getServer().getPluginManager().isPluginEnabled(\"PlaceholderAPI\");"));
        assertTrue(source.contains("runtime-placeholder-policy\", \"disabled-feature-or-missing-placeholderapi-registers-no-expansion"));
    }
}
