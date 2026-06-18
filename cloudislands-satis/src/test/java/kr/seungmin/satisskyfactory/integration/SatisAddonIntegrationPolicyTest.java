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
        assertEquals("external-plugin,built-in-feature-pack,built-in-compatible", SatisAddonIntegrationPolicy.SUPPORTED_PACKAGING_MODES);
        assertTrue(SatisAddonIntegrationPolicy.modeSupported("EXTERNAL_ADDON"));
        assertTrue(SatisAddonIntegrationPolicy.modeSupported("BUILT_IN_COMPATIBLE"));
        assertTrue(SatisAddonIntegrationPolicy.modeSupported("DISABLED"));
    }

    @Test
    void requiresCloudIslandsApiOnlyAndRootFeatureGates() {
        assertEquals("cloudislands-api-only-no-superiorskyblock2-runtime", SatisAddonIntegrationPolicy.API_POLICY);
        assertEquals("island-member-permission-location-upgrade-values-through-cloudislands-api-or-addon-spi", SatisAddonIntegrationPolicy.API_SURFACE_POLICY);
        assertEquals("no-direct-cloudislands-storage-runtime-or-world-owner-access", SatisAddonIntegrationPolicy.FORBIDDEN_DIRECT_ACCESS_POLICY);
        assertEquals("optional-content-layer-not-cloudislands-core-lifecycle-owner", SatisAddonIntegrationPolicy.OFFICIAL_FEATURE_PACK_POLICY);
        assertEquals("same-cloudislands-addon-spi-for-external-plugin-and-built-in-feature-pack", SatisAddonIntegrationPolicy.ADDON_SPI_POLICY);
        assertEquals("cloudislands-satis-owns-optional-machines-resource-nodes-contracts-research-market-and-placeholders", SatisAddonIntegrationPolicy.CONTENT_LAYER_POLICY);
        assertEquals("cloudislands-core-owns-island-lifecycle-routing-storage-protection-and-public-api", SatisAddonIntegrationPolicy.CORE_BOUNDARY_POLICY);
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
    void requiresPublicCloudIslandsApiDomainsAndBlocksDirectInternals() {
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("island-query"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("member-query"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("permission-query"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("active-location-query"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("upgrade-value-query"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("runtime-route-query"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("lifecycle-events"));
        assertTrue(SatisAddonIntegrationPolicy.apiDomainRequired("addon-state-storage"));

        assertTrue(SatisAddonIntegrationPolicy.directAccessForbidden("SuperiorSkyblock2-runtime-api"));
        assertTrue(SatisAddonIntegrationPolicy.directAccessForbidden("CloudIslands-core-service-internals"));
        assertTrue(SatisAddonIntegrationPolicy.directAccessForbidden("CloudIslands-storage-implementation"));
        assertTrue(SatisAddonIntegrationPolicy.directAccessForbidden("Paper-world-name-as-state-owner"));
        assertTrue(SatisAddonIntegrationPolicy.directAccessForbidden("Island-node-name-as-state-owner"));
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
        assertEquals("disabled-or-removed-preserves-addon-state-by-island-uuid", SatisAddonIntegrationPolicy.DATA_RETENTION_POLICY);
        assertEquals("disabled-feature-preserves-existing-state-and-skips-new-runtime-writes", SatisAddonIntegrationPolicy.FEATURE_DISABLE_DATA_POLICY);
        assertEquals("reenable-restores-state-from-shared-backend-by-island-uuid", SatisAddonIntegrationPolicy.REENABLE_POLICY);
        assertEquals("no-automatic-delete-on-disable-remove-or-feature-off", SatisAddonIntegrationPolicy.NO_AUTOMATIC_DELETE_POLICY);
        assertEquals("island-uuid-stable-node-world-cell-volatile", SatisAddonIntegrationPolicy.STATE_KEY_POLICY);
        assertEquals("cloudislands-island-uuid", SatisAddonIntegrationPolicy.PERSISTENT_ID_AUTHORITY);
        assertEquals("server-name,world-name,player-uuid", SatisAddonIntegrationPolicy.FORBIDDEN_PERSISTENT_OWNER_KEYS);
        assertEquals("active-node-world-center-are-remap-targets-not-state-owners", SatisAddonIntegrationPolicy.VOLATILE_PLACEMENT_POLICY);
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
                "existing-feature-state-is-preserved-and-not-deleted-while-feature-is-off",
                SatisAddonIntegrationPolicy.requiredScenarios().get("feature-off-data-retention")
        );
        assertEquals(
                "previous-addon-state-is-reloaded-from-shared-storage-by-island-uuid",
                SatisAddonIntegrationPolicy.requiredScenarios().get("addon-reenable")
        );
        assertEquals(
                "satis-state-uses-cloudislands-island-uuid-as-persistent-owner-key",
                SatisAddonIntegrationPolicy.requiredScenarios().get("island-id-storage")
        );
        assertEquals(
                "server-world-and-center-are-remapped-runtime-placement-not-persistent-identity",
                SatisAddonIntegrationPolicy.requiredScenarios().get("volatile-placement")
        );
        assertEquals(
                "island-member-permission-location-upgrade-data-come-from-cloudislands-api-or-addon-spi",
                SatisAddonIntegrationPolicy.requiredScenarios().get("api-surface")
        );
        assertEquals(
                "satis-does-not-read-cloudislands-storage-runtime-internals-or-node-ownership-directly",
                SatisAddonIntegrationPolicy.requiredScenarios().get("no-direct-internals")
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
                "expired-heartbeat-or-fencing-mismatch-blocks-duplicate-tick-and-replays-last-confirmed-state-on-target-node",
                SatisAddonIntegrationPolicy.requiredScenarios().get("node-crash-recovery")
        );
        assertEquals(
                "cloudislands-base-island-lifecycle-boots-without-addon-metadata-or-runtime-jar-and-reconnects-existing-addon-state-when-reinstalled",
                SatisAddonIntegrationPolicy.requiredScenarios().get("addon-jar-removed")
        );
        assertEquals(
                "legacy-skyblock-calls-are-replaced-by-cloudislands-api-or-addon-spi",
                SatisAddonIntegrationPolicy.requiredScenarios().get("no-superiorskyblock2")
        );
    }

    @Test
    void exposesOfficialFeaturePackBoundaryFromCommonPolicy() {
        assertEquals(
                "cloudislands-core-owns-island-lifecycle-routing-storage-protection-and-public-api",
                SatisAddonIntegrationPolicy.officialFeaturePackBoundaries().get("platform-layer")
        );
        assertEquals(
                "cloudislands-satis-owns-optional-machines-resource-nodes-contracts-research-market-and-placeholders",
                SatisAddonIntegrationPolicy.officialFeaturePackBoundaries().get("content-layer")
        );
        assertTrue(SatisAddonIntegrationPolicy.officialFeaturePackBoundarySummary().contains("failure-boundary=satis-failure-or-removal-must-not-stop-core-island-create-route-save-restore"));
    }

    @Test
    void exposesGoalCompletionCriteriaForRecentIntegrationRequirements() {
        assertTrue(SatisAddonIntegrationPolicy.completionCriteria().contains("addon-jar-and-cloudislands-addon-descriptor-ship-as-separate-addon-bundle-artifacts"));
        assertTrue(SatisAddonIntegrationPolicy.completionCriteria().contains("setup-database-supports-core-api-postgresql-mysql-mariadb-and-safe-fallback"));
        assertTrue(SatisAddonIntegrationPolicy.completionCriteria().contains("table-key-value-bulk-save-api-covers-global-and-island-addon-state"));
        assertTrue(SatisAddonIntegrationPolicy.completionCriteria().contains("command-list-renders-one-line-per-command-with-paging"));
        assertTrue(SatisAddonIntegrationPolicy.completionCriteria().contains("island-create-home-visit-and-soft-full-island-1-to-island-2-flows-are-pinned"));
        assertEquals(
                "operator-selects-core-api-postgresql-mysql-mariadb-or-safe-fallback-through-setup-database-config",
                SatisAddonIntegrationPolicy.operationScenarios().get("setup-database-mode")
        );
        assertEquals(
                "satis-uses-table-key-value-bulk-save-and-load-before-flattened-addon-state-fallback",
                SatisAddonIntegrationPolicy.operationScenarios().get("bulk-table-state-mode")
        );
        assertEquals(
                "factory-and-admin-command-list-render-one-command-per-line-with-page-navigation",
                SatisAddonIntegrationPolicy.operationScenarios().get("command-list-mode")
        );
        assertEquals(
                "island-1-soft-full-new-create-skips-to-ready-island-2-without-player-command-change",
                SatisAddonIntegrationPolicy.operationScenarios().get("soft-full-create-mode")
        );
    }

    @Test
    void publicPolicyCollectionsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.supportedModes().add("LEGACY"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.featureGates().add("legacy-skyblock"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.requiredApiDomains().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.forbiddenDirectAccessTargets().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.lifecycleEvents().add("legacy-event"));
        assertThrows(UnsupportedOperationException.class, () -> SatisAddonIntegrationPolicy.requiredScenarios().put("legacy", "disabled"));
    }
}
