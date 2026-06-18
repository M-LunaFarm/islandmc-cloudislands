package kr.lunaf.cloudislands.common.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SatisFeaturePackActivationPolicyTest {
    @Test
    void supportsExternalBuiltInCompatibleAndDisabledModes() {
        assertEquals(
            List.of("EXTERNAL_ADDON", "BUILT_IN_COMPATIBLE", "DISABLED"),
            SatisFeaturePackActivationPolicy.supportedModes()
        );
        assertEquals(
            "external-addon-and-built-in-compatible-use-same-root-gate-feature-gate-and-cloudislands-api-checks",
            SatisFeaturePackActivationPolicy.ACTIVATION_POLICY
        );
        assertEquals("EXTERNAL_ADDON", SatisFeaturePackActivationPolicy.normalizeMode("external-plugin"));
        assertEquals("BUILT_IN_COMPATIBLE", SatisFeaturePackActivationPolicy.normalizeMode("built-in-feature-pack"));
        assertEquals("DISABLED", SatisFeaturePackActivationPolicy.normalizeMode("off"));
    }

    @Test
    void externalAddonRequiresDescriptorAndCloudIslandsApi() {
        SatisFeaturePackActivationPolicy.ActivationDecision missingDescriptor = SatisFeaturePackActivationPolicy.decide(
            "external-addon",
            true,
            true,
            true,
            false
        );

        assertFalse(missingDescriptor.runtimeEnabled());
        assertEquals("external-addon-descriptor-missing", missingDescriptor.blockReason());

        SatisFeaturePackActivationPolicy.ActivationDecision active = SatisFeaturePackActivationPolicy.decide(
            "external-addon",
            true,
            true,
            true,
            true
        );

        assertTrue(active.runtimeEnabled());
        assertEquals("external-addon-runtime", active.runtimeShape());
        assertEquals("none", active.blockReason());
    }

    @Test
    void builtInCompatibleModeUsesSameGatesWithoutExternalJarDescriptor() {
        SatisFeaturePackActivationPolicy.ActivationDecision active = SatisFeaturePackActivationPolicy.decide(
            "built-in-compatible",
            true,
            true,
            true,
            false
        );

        assertTrue(active.runtimeEnabled());
        assertEquals("BUILT_IN_COMPATIBLE", active.mode());
        assertEquals("built-in-compatible-runtime", active.runtimeShape());
        assertEquals(
            "built-in-compatible-runtime-does-not-require-external-addon-jar-but-keeps-addon-spi-gates",
            SatisFeaturePackActivationPolicy.BUILT_IN_DESCRIPTOR_POLICY
        );
    }

    @Test
    void disabledRootFeatureOrMissingApiBlocksRuntimeWithExplicitReason() {
        assertEquals(
            "mode-disabled",
            SatisFeaturePackActivationPolicy.decide("disabled", true, true, true, true).blockReason()
        );
        assertEquals(
            "root-disabled",
            SatisFeaturePackActivationPolicy.decide("external-addon", false, true, true, true).blockReason()
        );
        assertEquals(
            "feature-disabled",
            SatisFeaturePackActivationPolicy.decide("external-addon", true, false, true, true).blockReason()
        );
        assertEquals(
            "cloudislands-api-missing",
            SatisFeaturePackActivationPolicy.decide("external-addon", true, true, false, true).blockReason()
        );
        assertEquals(
            "unsupported-mode",
            SatisFeaturePackActivationPolicy.decide("legacy-superiorskyblock2", true, true, true, true).blockReason()
        );
    }
}
