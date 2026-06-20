package kr.lunaf.cloudislands.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import kr.lunaf.cloudislands.api.model.CloudIslandsStatusSnapshot;

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
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("runtime-api-version"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("semantic-version-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("deprecation-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("compatibility-levels"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("deprecation-removal-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("event-delivery-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("threading-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("core-failure-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("timeout-retry-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("compatibility-testkit-policy"));
        assertTrue(CloudIslandsApiContract.requiredMetadataKeys().contains("integration-port-policy"));

        assertEquals("external-plugin,built-in-feature-pack,built-in-compatible", CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING);
        assertEquals("addons-may-run-as-external-plugins-or-built-in-feature-packs-through-the-same-spi", CloudIslandsApiContract.ADDON_PACKAGING_POLICY);
        assertEquals("addon-descriptor-may-be-embedded-in-jar-or-distributed-as-sidecar-cloudislands-addon-yml", CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY);
        assertEquals("distAddons-and-distAddonBundle-package-addon-jars-and-descriptor-sidecars-separately-from-required-core", CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY);
        assertEquals("missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore", CloudIslandsApiContract.ADDON_REMOVAL_POLICY);
        assertEquals("reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid", CloudIslandsApiContract.ADDON_RECONNECT_POLICY);
        assertEquals("1.1.0", CloudIslandsApiContract.RUNTIME_API_VERSION);
        assertEquals("major-version-breaks-binary-api-minor-adds-compatible-api-patch-fixes-only", CloudIslandsApiContract.SEMANTIC_VERSION_POLICY);
        assertEquals("deprecated-api-remains-for-at-least-one-minor-release-before-removal", CloudIslandsApiContract.DEPRECATION_POLICY);
        assertEquals("compatible,runtime-too-old,major-version-mismatch,invalid-version", CloudIslandsApiContract.COMPATIBILITY_LEVELS);
        assertEquals("deprecated-api-removal-requires-major-bump-or-one-full-minor-window", CloudIslandsApiContract.DEPRECATION_REMOVAL_POLICY);
        assertEquals("global-events-are-at-least-once-delivered-and-consumers-must-deduplicate-by-event-id", CloudIslandsApiContract.EVENT_DELIVERY_POLICY);
        assertEquals("api-futures-complete-off-main-thread-paper-callers-must-schedule-bukkit-world-and-player-access", CloudIslandsApiContract.THREADING_POLICY);
        assertEquals("core-unavailable-fails-closed-for-writes-and-may-return-marked-stale-snapshots-for-reads", CloudIslandsApiContract.CORE_FAILURE_POLICY);
        assertEquals("typed-core-client-uses-bounded-timeouts-and-retries-read-requests-only-unless-idempotency-key-is-present", CloudIslandsApiContract.TIMEOUT_RETRY_POLICY);
        assertEquals("addons-validate-against-cloudislands-testkit-contract-fixtures-before-certification", CloudIslandsApiContract.COMPATIBILITY_TESTKIT_POLICY);
        assertEquals("external-hooks-use-applyValidated-so-core-state-mutations-require-island-uuid-node-id-runtime-fencing-token-node-ownership-and-idempotency-key", CloudIslandsApiContract.INTEGRATION_PORT_POLICY);
    }

    @Test
    void contractMetadataMatchesTheRequiredKeySet() {
        assertEquals("compatible", CloudIslandsApiContract.metadataCompatibilityStatus(CloudIslandsApiContract.metadata()));
        assertEquals(CloudIslandsApiContract.ADDON_SUPPORTED_PACKAGING, CloudIslandsApiContract.metadata().get("addon-supported-packaging"));
        assertEquals(CloudIslandsApiContract.ADDON_DESCRIPTOR_POLICY, CloudIslandsApiContract.metadata().get("addon-descriptor-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_DISTRIBUTION_POLICY, CloudIslandsApiContract.metadata().get("addon-distribution-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_REMOVAL_POLICY, CloudIslandsApiContract.metadata().get("addon-removal-policy"));
        assertEquals(CloudIslandsApiContract.ADDON_RECONNECT_POLICY, CloudIslandsApiContract.metadata().get("addon-reconnect-policy"));
        assertEquals(CloudIslandsApiContract.RUNTIME_API_VERSION, CloudIslandsApiContract.metadata().get("runtime-api-version"));
        assertEquals(CloudIslandsApiContract.SEMANTIC_VERSION_POLICY, CloudIslandsApiContract.metadata().get("semantic-version-policy"));
        assertEquals(CloudIslandsApiContract.DEPRECATION_POLICY, CloudIslandsApiContract.metadata().get("deprecation-policy"));
        assertEquals(CloudIslandsApiContract.COMPATIBILITY_LEVELS, CloudIslandsApiContract.metadata().get("compatibility-levels"));
        assertEquals(CloudIslandsApiContract.DEPRECATION_REMOVAL_POLICY, CloudIslandsApiContract.metadata().get("deprecation-removal-policy"));
        assertEquals(CloudIslandsApiContract.EVENT_DELIVERY_POLICY, CloudIslandsApiContract.metadata().get("event-delivery-policy"));
        assertEquals(CloudIslandsApiContract.THREADING_POLICY, CloudIslandsApiContract.metadata().get("threading-policy"));
        assertEquals(CloudIslandsApiContract.CORE_FAILURE_POLICY, CloudIslandsApiContract.metadata().get("core-failure-policy"));
        assertEquals(CloudIslandsApiContract.TIMEOUT_RETRY_POLICY, CloudIslandsApiContract.metadata().get("timeout-retry-policy"));
        assertEquals(CloudIslandsApiContract.COMPATIBILITY_TESTKIT_POLICY, CloudIslandsApiContract.metadata().get("compatibility-testkit-policy"));
        assertEquals(CloudIslandsApiContract.INTEGRATION_PORT_POLICY, CloudIslandsApiContract.metadata().get("integration-port-policy"));
    }

    @Test
    void statusSnapshotMetadataMatchesTheRequiredKeySet() {
        CloudIslandsStatusSnapshot snapshot = new CloudIslandsStatusSnapshot(
            "paper",
            "ISLAND_NODE",
            "island-1",
            "1.0.0",
            true,
            true,
            true,
            true,
            true,
            true,
            1,
            2,
            0,
            Instant.parse("2026-01-01T00:00:00Z")
        );

        assertTrue(snapshot.contractCompatible(), snapshot.missingContractMetadataKeys().toString());
        assertEquals("compatible", snapshot.contractCompatibilityStatus());
    }
}
