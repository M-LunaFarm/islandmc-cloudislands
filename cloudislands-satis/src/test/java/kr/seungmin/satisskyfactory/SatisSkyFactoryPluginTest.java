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
        assertEquals("paper-plugin-hard-depends-cloudislands-but-uses-public-cloudislands-api", metadata.get("external-addon-boundary"));
        assertEquals("same-cloudislands-addon-spi-no-core-internals-no-standalone-runtime", metadata.get("built-in-compatible-boundary"));
        assertEquals("external-plugin-and-built-in-compatible-share-public-addon-spi-and-feature-gates", metadata.get("packaging-boundary-policy"));
        assertEquals("same-cloudislands-addon-spi-for-external-plugin-and-built-in-feature-pack", metadata.get("addon-spi-policy"));
        assertEquals("true", metadata.get("addon-removal-safe"));
        assertEquals("missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore", metadata.get("addon-removal-policy"));
        assertEquals("reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid", metadata.get("addon-reconnect-policy"));
        assertEquals("stop-dirty-save-loop-clear-publishers-detach-service-references", metadata.get("addon-removal-dirty-save-detach-policy"));
        assertEquals("recreate-dirty-save-service-and-reattach-service-references-before-restart", metadata.get("addon-removal-dirty-save-reattach-policy"));
        assertEquals("reload-reenable-starts-runtime-when-database-is-not-initialized", metadata.get("addon-reload-runtime-restart-policy"));
        assertEquals("core-refresh-reapplies-satis-runtime-state-after-refresh-success-or-fallback", metadata.get("addon-core-refresh-reapply-policy"));
        assertEquals("core-refresh-reapplies-satis-runtime-state-after-refresh-success-or-fallback", metadata.get("runtime-core-refresh-reapply-policy"));
        assertEquals("preserve-addon-state-by-island-uuid", metadata.get("addon-data-retention"));
        assertEquals("false", metadata.get("addon-runtime-owns-islands"));
        assertEquals("CORE_API", metadata.get("addon-default-database-mode"));
        assertEquals("false", metadata.get("superior-runtime-dependency"));
        assertEquals("true", metadata.get("cloudislands-api-only"));
        assertEquals("true", metadata.get("config-gated"));
        assertEquals("setup.satis.mode,addons.cloudislands-satis.integration.mode,integration.mode", metadata.get("integration-mode-config-paths"));
        assertEquals("external-addon-and-built-in-compatible-use-same-root-gate-feature-gate-and-cloudislands-api-checks", metadata.get("feature-pack-activation-policy"));
        assertEquals("EXTERNAL_ADDON,BUILT_IN_COMPATIBLE,DISABLED", metadata.get("feature-pack-activation-supported-modes"));
        assertTrue(metadata.get("activation-state-keys").contains("runtime-feature-pack-activation-policy"));
        assertTrue(metadata.get("activation-state-keys").contains("runtime-feature-pack-block-reason"));
        assertTrue(metadata.get("activation-state-keys").contains("runtime-disable-activation-block-reason"));
        assertTrue(metadata.get("activation-state-keys").contains("last-preflush-activation-block-reason"));
        assertEquals("external-addon-runtime", metadata.get("feature-pack-runtime-shape"));
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
        assertTrue(metadata.get("dirty-save-state-keys").contains("addon-removal-dirty-save-detach-policy"));
        assertTrue(metadata.get("dirty-save-state-keys").contains("addon-removal-dirty-save-reattach-policy"));
        assertTrue(metadata.get("dirty-save-state-keys").contains("addon-reload-runtime-restart-policy"));
        assertTrue(metadata.get("dirty-save-state-keys").contains("addon-core-refresh-reapply-policy"));
        assertEquals("disable-plugin-clear-features-register-no-components", metadata.get("missing-cloudislands-behavior"));
        assertEquals("no-hardcoded-island-node-count", metadata.get("island-state-node-count-policy"));
        assertEquals("node-id-is-routing-context-not-addon-state-key", metadata.get("island-state-node-identity-policy"));
        assertEquals("five-or-six-island-nodes-are-supported-when-each-node-has-unique-node-id-unique-velocity-server-name-shared-storage-and-route-candidate-readiness", metadata.get("island-state-five-six-node-policy"));
        assertEquals("seven-or-more-island-nodes-use-the-same-live-route-candidate-rules-with-no-player-command-change", metadata.get("island-state-seven-plus-node-policy"));
        assertEquals("server-a-soft-full-or-draining-core-api-allocates-new-island-on-server-b-and-player-still-runs-logical-island-command", metadata.get("island-state-ab-server-new-island-scenario"));
        assertEquals("existing-island-deactivates-on-server-a-saves-satis-state-by-island-uuid-activates-on-server-b-and-remaps-volatile-placement", metadata.get("island-state-ab-server-existing-island-scenario"));
        assertEquals("config-reload-after-satis-disable-restarts-runtime-when-database-is-not-initialized", metadata.get("island-state-reload-reenable-scenario"));
        assertEquals("core-api-owns-route-ticket-create-consume-satis-records-diagnostics-only", metadata.get("route-authority-policy"));
        assertEquals("player-facing-satis-output-never-includes-route-ticket-node-server-world-cell", metadata.get("route-ticket-privacy-policy"));
        assertEquals("core-api-stores-addon-metadata-and-opaque-state-without-satis-business-rules", metadata.get("core-api-metadata-state-policy"));
        assertEquals("core-api-does-not-interpret-machines-factories-generators-contracts-research-or-market-rules", metadata.get("core-api-forbidden-content-policy"));
        assertTrue(metadata.get("core-api-addon-state-boundaries").contains("state-contract=core-api-stores-opaque-table-key-value-addon-state-scoped-by-island-uuid-and-addon-id"));
        assertEquals("my-island-other-island-ranking-visit-settings-warps-use-logical-core-api-backed-flows", metadata.get("player-surface-policy"));
        assertEquals("player-facing-satis-ui-hides-island-node-server-world-cell-and-route-ticket", metadata.get("player-surface-hide-policy"));
        assertEquals("velocity-owns-global-island-routing-commands-paper-satis-handles-local-addon-ui-only", metadata.get("player-surface-command-owner-policy"));
        assertEquals("velocity-modern-forwarding-with-shared-secret-required-for-paper-node-identity-trust", metadata.get("velocity-forwarding-policy"));
        assertEquals("paper-island-nodes-run-online-mode-false-only-behind-velocity-and-block-direct-backend-access", metadata.get("paper-backend-access-policy"));
        assertEquals("proxy-plugin-messages-are-handled-not-forwarded-to-prevent-backend-spoofing", metadata.get("plugin-message-security-policy"));
        assertEquals("postgresql-or-core-api-shared-state-is-authoritative-for-satis-state", metadata.get("runtime-authoritative-store-policy"));
        assertEquals("redis-cache-stream-locks-are-advisory-never-satis-state-authority", metadata.get("runtime-redis-advisory-policy"));
        assertEquals("redis-outage-keeps-last-confirmed-shared-state-authoritative-and-disables-cache-only-assumptions", metadata.get("runtime-redis-failure-policy"));
        assertEquals("satis-records-addon-state-references-only-cloudislands-core-owns-world-bundle-storage", metadata.get("object-storage-access-policy"));
        assertEquals("core-world-bundle-manifest-must-include-island-runtime-and-satis-addon-state-references", metadata.get("bundle-manifest-policy"));
        assertEquals("restore-requires-manifest-and-checksums-sha256-match-before-satis-state-rehydrate", metadata.get("bundle-checksum-policy"));
        assertEquals("pre-restore-snapshot-then-core-restore-then-satis-addon-state-rehydrate", metadata.get("bundle-restore-policy"));
        assertEquals("checksum-or-manifest-mismatch-quarantines-bundle-and-keeps-last-confirmed-satis-state", metadata.get("bundle-quarantine-policy"));
        assertEquals("cloudislands-core-owns-island-lifecycle-state-machine-satis-reacts-to-events-only", metadata.get("lifecycle-authority-policy"));
        assertEquals("error-or-quarantine-state-suspends-satis-runtime-and-keeps-last-confirmed-addon-state", metadata.get("lifecycle-error-policy"));
        assertEquals("repaired-or-restored-state-rehydrates-satis-after-core-confirms-runtime-owner", metadata.get("lifecycle-recovery-policy"));
        assertTrue(metadata.get("satis-operation-scenarios").contains("a-b-server-new-island-mode=server-a-soft-full-or-draining-core-api-allocates-new-island-on-server-b-and-player-still-runs-logical-island-command"));
        assertTrue(metadata.get("satis-completion-criteria").contains("a-server-b-server-new-and-existing-island-flows-are-pinned"));
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
    void resourceConfigDocumentsFeaturePackActivationContract() throws Exception {
        String addonDescriptor = Files.readString(Path.of("src/main/resources/cloudislands-addon.yml"));
        String config = Files.readString(Path.of("src/main/resources/config.yml"));

        assertTrue(addonDescriptor.contains("activation-policy: external-addon-and-built-in-compatible-use-same-root-gate-feature-gate-and-cloudislands-api-checks"));
        assertTrue(addonDescriptor.contains("external-descriptor-policy: external-addon-runtime-requires-cloudislands-addon-descriptor"));
        assertTrue(addonDescriptor.contains("built-in-descriptor-policy: built-in-compatible-runtime-does-not-require-external-addon-jar-but-keeps-addon-spi-gates"));
        assertTrue(addonDescriptor.contains("activation-block-reasons: unsupported-mode,mode-disabled,root-disabled,feature-disabled,cloudislands-api-missing,external-addon-descriptor-missing"));
        assertTrue(config.contains("activation-policy: external-addon-and-built-in-compatible-use-same-root-gate-feature-gate-and-cloudislands-api-checks"));
        assertTrue(config.contains("- external-addon-descriptor-missing"));
    }

    @Test
    void runtimePlanUsesCloudIslandsApiPresenceForStandaloneGuard() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/SatisSkyFactoryPlugin.java"));
        String adminSource = Files.readString(Path.of("src/main/java/kr/seungmin/satisskyfactory/command/AdminFactoryCommand.java"));

        assertTrue(source.contains("operationalFeatureEnabled(\"addon-state\"),\n                cloudIslandsApi != null"));
        assertTrue(source.contains("SatisAddonIntegrationPolicy.activationDecision("));
        assertTrue(source.contains("runtime-feature-pack-block-reason"));
        assertTrue(source.contains("runtime-disable-activation-block-reason"));
        assertTrue(source.contains("last-preflush-activation-block-reason"));
        assertTrue(source.contains("preflush-activation-block-reason"));
        assertTrue(source.contains("CloudIslands Satis runtime blocked by activation policy"));
        assertTrue(adminSource.contains("runtime-feature-pack-activation-policy"));
        assertTrue(adminSource.contains("runtime-feature-pack-activation-mode"));
        assertTrue(adminSource.contains("runtime-feature-pack-runtime-enabled"));
        assertTrue(adminSource.contains("runtime-feature-pack-runtime-shape"));
        assertTrue(adminSource.contains("runtime-feature-pack-block-reason"));
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
