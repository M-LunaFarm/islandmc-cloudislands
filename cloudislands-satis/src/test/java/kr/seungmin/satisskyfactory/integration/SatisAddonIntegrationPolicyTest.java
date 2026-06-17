package kr.seungmin.satisskyfactory.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisAddonIntegrationPolicyTest {
    @Test
    void prefersOfficialExternalAddonWithoutClosingBuiltInCompatiblePath() {
        assertEquals("cloudislands-satis", SatisAddonIntegrationPolicy.ADDON_ID);
        assertEquals("EXTERNAL_ADDON", SatisAddonIntegrationPolicy.RECOMMENDED_MODE);
        assertTrue(SatisAddonIntegrationPolicy.modeSupported("EXTERNAL_ADDON"));
        assertTrue(SatisAddonIntegrationPolicy.modeSupported("BUILT_IN_COMPATIBLE"));
        assertTrue(SatisAddonIntegrationPolicy.modeSupported("DISABLED"));
    }

    @Test
    void requiresCloudIslandsApiOnlyAndRootFeatureGates() {
        assertEquals("cloudislands-api-only-no-superiorskyblock2-runtime", SatisAddonIntegrationPolicy.API_POLICY);
        assertEquals("cloudislands-api-required-no-standalone-island-runtime", SatisAddonIntegrationPolicy.CLOUDISLANDS_REQUIRED_POLICY);
        assertEquals("bootstrap-or-services-manager", SatisAddonIntegrationPolicy.API_RESOLUTION_POLICY);
        assertEquals("disable-plugin-clear-features-register-no-components", SatisAddonIntegrationPolicy.MISSING_API_BEHAVIOR);
        assertEquals("CloudIslands", SatisAddonIntegrationPolicy.RUNTIME_HARD_DEPEND_PLUGIN);
        assertEquals("false", SatisAddonIntegrationPolicy.STANDALONE_ISLAND_MANAGEMENT);
        assertEquals("addons.cloudislands-satis.enabled&&satis.enabled", SatisAddonIntegrationPolicy.ROOT_GATE_POLICY);
        assertEquals(
                "root-disabled-forces-every-feature-off-child-disabled-skips-commands-gui-listeners-tickers-writes",
                SatisAddonIntegrationPolicy.FEATURE_GATE_POLICY
        );
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("machines"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("resource-nodes"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("gui"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("storage"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("market"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("contracts"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("research"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("maintenance"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("placeholders"));
        assertTrue(SatisAddonIntegrationPolicy.featureGateRequired("addon-state"));
    }

    @Test
    void coversCloudIslandsLifecycleEventsNeededBySatis() {
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-created"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-pre-activate"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-activated"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-deactivation-request"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-deactivated"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-migration-request"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-migrated"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-member-changed"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-permission-changed"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-level-recalculate"));
        assertTrue(SatisAddonIntegrationPolicy.lifecycleEventRequired("island-worth-changed"));
    }

    @Test
    void pinsAbNodeMoveAndRemovalSafetyScenarios() {
        assertEquals("core-api-table-key-value-or-shared-database", SatisAddonIntegrationPolicy.DATA_AUTHORITY);
        assertEquals("save-on-source-node-restore-on-target-node-by-island-uuid", SatisAddonIntegrationPolicy.NODE_MOVE_POLICY);
        assertEquals("addon-removed-or-disabled-never-blocks-cloudislands-base-island-lifecycle", SatisAddonIntegrationPolicy.REMOVAL_POLICY);
        assertEquals("island-uuid-stable-node-world-cell-volatile", SatisAddonIntegrationPolicy.STATE_KEY_POLICY);
        assertEquals(
                "factory-upgrade-menu-progress-state-restores-on-target-node-from-shared-state",
                SatisAddonIntegrationPolicy.requiredScenarios().get("a-b-node-move")
        );
        assertEquals(
                "base-cloudislands-create-visit-protect-save-restore-continues-without-satis-runtime-components",
                SatisAddonIntegrationPolicy.requiredScenarios().get("satis-disabled")
        );
        assertEquals(
                "disabled-feature-registers-no-command-gui-listener-task-or-write-path",
                SatisAddonIntegrationPolicy.requiredScenarios().get("partial-features")
        );
        assertEquals(
                "cloudislands-boots-without-satis-jar-and-discovers-satis-through-addon-api-when-installed",
                SatisAddonIntegrationPolicy.requiredScenarios().get("external-addon")
        );
        assertEquals(
                "satis-runtime-does-not-start-and-registers-no-commands-listeners-tickers-or-writers",
                SatisAddonIntegrationPolicy.requiredScenarios().get("missing-cloudislands-api")
        );
        assertEquals(
                "legacy-skyblock-calls-are-replaced-by-cloudislands-api-or-addon-spi",
                SatisAddonIntegrationPolicy.requiredScenarios().get("no-superiorskyblock2")
        );
    }

    @Test
    void publicPolicyCollectionsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.supportedModes().add("LEGACY"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.featureGates().add("legacy-skyblock"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.lifecycleEvents().add("legacy-event"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.requiredScenarios().put("legacy", "disabled"));
    }
}
