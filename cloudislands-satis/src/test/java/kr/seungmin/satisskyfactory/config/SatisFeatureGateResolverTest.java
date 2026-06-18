package kr.seungmin.satisskyfactory.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class SatisFeatureGateResolverTest {
    @Test
    void parentAddonSwitchDisablesEveryChildFeature() {
        YamlConfiguration config = defaults();
        config.set("addons.cloudislands-satis.enabled", false);
        config.set("satis.features.machines", true);
        config.set("addons.cloudislands-satis.features.machines", true);

        assertFalse(SatisFeatureGateResolver.rootEnabled(config));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "machines"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "contracts"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "placeholders"));
    }

    @Test
    void disabledIntegrationModeRegistersNoRuntimeFeatures() {
        YamlConfiguration config = defaults();
        config.set("integration.mode", "DISABLED");

        assertFalse(SatisFeatureGateResolver.rootEnabled(config));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "commands"));
    }

    @Test
    void setupOrAddonIntegrationModeOverridesDefaultRuntimeMode() {
        YamlConfiguration setupDisabled = defaults();
        setupDisabled.set("setup.satis.mode", "disabled");
        setupDisabled.set("integration.mode", "EXTERNAL_ADDON");

        YamlConfiguration addonBuiltIn = defaults();
        addonBuiltIn.set("addons.cloudislands-satis.integration.mode", "built-in-compatible");

        assertEquals("DISABLED", SatisFeatureGateResolver.integrationMode(setupDisabled, "EXTERNAL_ADDON"));
        assertFalse(SatisFeatureGateResolver.rootEnabled(setupDisabled));
        assertEquals("integration-mode-disabled", SatisFeatureGateResolver.rootBlockReason(setupDisabled));
        assertEquals("BUILT_IN_COMPATIBLE", SatisFeatureGateResolver.integrationMode(addonBuiltIn, "EXTERNAL_ADDON"));
        assertTrue(SatisFeatureGateResolver.rootEnabled(addonBuiltIn));
    }

    @Test
    void legacyIntegrationSwitchAlsoDisablesTheRuntimeRoot() {
        YamlConfiguration config = defaults();
        config.set("integration.enabled", false);

        assertFalse(SatisFeatureGateResolver.rootEnabled(config));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "machines"));
    }

    @Test
    void legacyAliasDisablesCanonicalFeature() {
        YamlConfiguration config = defaults();
        config.set("satis.features.generators", false);

        assertEquals("resource-nodes", SatisFeatureGateResolver.canonical("generators"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "resource-nodes"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "generators"));
    }

    @Test
    void legacyFeaturePathDisablesModernFeatureGate() {
        YamlConfiguration config = defaults();
        config.set("features.placeholders", false);

        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "placeholders"));
    }

    @Test
    void childFeatureRequiresConfiguredDependency() {
        YamlConfiguration config = defaults();
        config.set("satis.features.storage", false);
        config.set("satis.features.market", true);
        config.set("satis.features.contracts", true);

        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "market"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "contracts"));
        assertTrue(SatisFeatureGateResolver.featureEnabled(config, "research"));
    }

    @Test
    void routeEventsRequireAddonState() {
        YamlConfiguration config = defaults();
        config.set("satis.features.addon-state", false);
        config.set("satis.features.route-events", true);

        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "route-events"));
    }

    @Test
    void lifecycleSubFeaturesRequireLifecycleFeature() {
        YamlConfiguration config = defaults();
        config.set("satis.features.lifecycle", false);

        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "members"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "permissions"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "level-values"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "warps"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "biomes"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "chat"));
        assertFalse(SatisFeatureGateResolver.featureEnabled(config, "templates"));
    }

    @Test
    void exposesConfigGateMetadataForAdminAndAddonDescriptors() {
        assertEquals(
                List.of("satis.features.", "addons.cloudislands-satis.features.", "features."),
                SatisFeatureGateResolver.featureRoots()
        );
        assertEquals(
                "satis.features.,addons.cloudislands-satis.features.,features.",
                SatisFeatureGateResolver.featureRootMetadata()
        );
        assertEquals(
                List.of("satis.enabled", "integration.enabled", "addons.cloudislands-satis.enabled", "setup.satis.mode|addons.cloudislands-satis.integration.mode|integration.mode!=DISABLED"),
                SatisFeatureGateResolver.rootGates()
        );
        assertEquals(
                "satis.enabled&&integration.enabled&&addons.cloudislands-satis.enabled&&setup.satis.mode|addons.cloudislands-satis.integration.mode|integration.mode!=DISABLED",
                SatisFeatureGateResolver.rootGateMetadata()
        );
        assertEquals(
                List.of("setup.satis.mode", "addons.cloudislands-satis.integration.mode", "integration.mode"),
                SatisFeatureGateResolver.integrationModePaths()
        );
        assertEquals(
                "root-gates-disable-all-satis-runtime-and-feature-roots-disable-their-runtime-components",
                SatisFeatureGateResolver.configGatePolicy()
        );
    }

    private YamlConfiguration defaults() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("satis.enabled", true);
        config.set("addons.cloudislands-satis.enabled", true);
        config.set("integration.mode", "EXTERNAL_ADDON");
        return config;
    }
}
