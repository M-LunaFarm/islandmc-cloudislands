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
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("semantic-version-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("deprecation-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("event-delivery-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("threading-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("core-failure-policy"));

        assertEquals("external-plugin,built-in-feature-pack,built-in-compatible", CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING);
        assertEquals("addons-may-run-as-external-plugins-or-built-in-feature-packs-through-the-same-spi", CloudIslandsApiContract.ADDON_PACKAGING_POLICY);
        assertEquals("addon-descriptor-may-be-embedded-in-jar-or-distributed-as-sidecar-cloudislands-addon-yml", CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY);
        assertEquals("distAddons-and-distAddonBundle-package-addon-jars-and-descriptor-sidecars-separately-from-required-core", CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY);
        assertEquals("missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore", CloudIslandsApiContract.ADDON_REMOVAL_POLICY);
        assertEquals("reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid", CloudIslandsApiContract.ADDON_RECONNECT_POLICY);
        assertEquals("major-version-breaks-binary-api-minor-adds-compatible-api-patch-fixes-only", CloudIslandsApiContract.SEMANTIC_VERSION_POLICY);
        assertEquals("deprecated-api-remains-for-at-least-one-minor-release-before-removal", CloudIslandsApiContract.DEPRECATION_POLICY);
        assertEquals("global-events-are-at-least-once-delivered-and-consumers-must-deduplicate-by-event-id", CloudIslandsApiContract.EVENT_DELIVERY_POLICY);
        assertEquals("api-futures-complete-off-main-thread-paper-callers-must-schedule-bukkit-world-and-player-access", CloudIslandsApiContract.THREADING_POLICY);
        assertEquals("core-unavailable-fails-closed-for-writes-and-may-return-marked-stale-snapshots-for-reads", CloudIslandsApiContract.CORE_FAILURE_POLICY);
    }

    @Test
    void contractMetadataMatchesTheRequiredKeySet() {
        assertEquals("compatible", CloudIslandsApiContract.metadataCompatibilityStatus(CloudIslandsApiContract.metadata()));
        assertEquals(CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING, CloudIslandsApiContract.metadata().get("addon-supported-packaging"));
        assertEquals(CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY, CloudIslandsApiContract.metadata().get("addon-descriptor-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY, CloudIslandsApiContract.metadata().get("addon-distribution-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_REMOVAL_POLICY, CloudIslandsApiContract.metadata().get("addon-removal-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_RECONNECT_POLICY, CloudIslandsApiContract.metadata().get("addon-reconnect-policy"));
        assertEquals(CloudIslandsApiContract.SEMANTIC_VERSION_POLICY, CloudIslandsApiContract.metadata().get("semantic-version-policy"));
        assertEquals(CloudIslandsApiContract.DEPRECATION_POLICY, CloudIslandsApiContract.metadata().get("deprecation-policy"));
        assertEquals(CloudIslandsApiContract.EVENT_DELIVERY_POLICY, CloudIslandsApiContract.metadata().get("event-delivery-policy"));
        assertEquals(CloudIslandsApiContract.THREADING_POLICY, CloudIslandsApiContract.metadata().get("threading-policy"));
        assertEquals(CloudIslandsApiContract.CORE_FAILURE_POLICY, CloudIslandsApiContract.metadata().get("core-failure-policy"));
    }
}
