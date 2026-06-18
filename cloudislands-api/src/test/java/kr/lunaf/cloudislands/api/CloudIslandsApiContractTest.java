package kr.lunaf.cloudislands.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudIslandsApiContractTest {
    @Test
    void includesAddonPackagingAndDescriptorContractInRequiredMetadata() {
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("addon-packaging-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("addon-supported-packaging"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("addon-descriptor-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("addon-distribution-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("addon-removal-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("addon-reconnect-policy"));

        assertEquals("external-plugin,built-in-feature-pack,built-in-compatible", CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING);
        assertEquals("addons-may-run-as-external-plugins-or-built-in-feature-packs-through-the-same-spi", CloudIslandsApiContract.ADDON_PACKAGING_POLICY);
        assertEquals("addon-descriptor-may-be-embedded-in-jar-or-distributed-as-sidecar-cloudislands-addon-yml", CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY);
        assertEquals("distAddons-and-distAddonBundle-package-addon-jars-and-descriptor-sidecars-separately-from-required-core", CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY);
        assertEquals("missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore", CloudIslandsApiContract.ADDON_REMOVAL_POLICY);
        assertEquals("reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid", CloudIslandsApiContract.ADDON_RECONNECT_POLICY);
    }

    @Test
    void contractMetadataMatchesTheRequiredKeySet() {
        assertEquals("compatible", CloudIslandsApiContract.metadataCompatibilityStatus(CloudIslandsApiContract.metadata()));
        assertEquals(CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING, CloudIslandsApiContract.metadata().get("addon-supported-packaging"));
        assertEquals(CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY, CloudIslandsApiContract.metadata().get("addon-descriptor-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY, CloudIslandsApiContract.metadata().get("addon-distribution-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_REMOVAL_POLICY, CloudIslandsApiContract.metadata().get("addon-removal-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_RECONNECT_POLICY, CloudIslandsApiContract.metadata().get("addon-reconnect-policy"));
    }
}
