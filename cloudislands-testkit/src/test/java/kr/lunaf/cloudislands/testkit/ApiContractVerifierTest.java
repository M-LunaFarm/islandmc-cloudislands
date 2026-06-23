package kr.lunaf.cloudislands.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.compat.ApiCompatibilityStatus;
import kr.lunaf.cloudislands.api.model.CloudIslandsAddonSnapshot;
import org.junit.jupiter.api.Test;

class ApiContractVerifierTest {
    @Test
    void runtimeContractMetadataPassesTheSharedContract() {
        ApiContractVerification verification = ApiContractVerifier.verifyRuntimeMetadata(CloudIslandsApiContract.metadata());

        assertTrue(verification.passed(), verification.failures().toString());
        assertEquals("compatible", verification.contractMetadataStatus());
        assertEquals(ApiCompatibilityStatus.COMPATIBLE, verification.apiCompatibility().status());
    }

    @Test
    void apiCompatibilityCliReportCarriesReleaseGateDecision() {
        ApiContractVerification verification = ApiContractVerifier.verifyRuntimeMetadata(CloudIslandsApiContract.metadata());
        String report = ApiCompatibilityCheckCli.reportJson(verification);

        assertTrue(report.contains("\"passed\":true"));
        assertTrue(report.contains("\"apiCompatibilityStatus\":\"compatible\""));
        assertTrue(report.contains(CloudIslandsApiContract.SEMANTIC_VERSION_POLICY));
        assertTrue(report.contains(CloudIslandsApiContract.COMPATIBILITY_TESTKIT_POLICY));
    }

    @Test
    void runtimeContractReportsMissingKeys() {
        Map<String, String> metadata = new HashMap<>(CloudIslandsApiContract.metadata());
        metadata.remove("event-delivery-policy");

        ApiContractVerification verification = ApiContractVerifier.verifyRuntimeMetadata(metadata);

        assertFalse(verification.passed());
        assertTrue(verification.missingContractMetadataKeys().contains("event-delivery-policy"));
        assertTrue(verification.failures().toString().contains("missing-contract-metadata:event-delivery-policy"));
    }

    @Test
    void addonSnapshotPassesWithStandardAndPrefixedRuntimeMetadata() {
        Map<String, String> addonMetadata = ApiContractVerifier.addonCertificationMetadata(standardAddonMetadata(), CloudIslandsApiContract.metadata());
        CloudIslandsAddonSnapshot addon = new CloudIslandsAddonSnapshot(
            "example-addon",
            "Example Addon",
            "1.0.0",
            true,
            Instant.EPOCH,
            Map.of("lifecycle", true),
            addonMetadata
        );

        ApiContractVerification verification = ApiContractVerifier.verifyAddon(addon);

        assertTrue(verification.passed(), verification.failures().toString());
        assertEquals(CloudIslandsApiContract.RUNTIME_API_VERSION, verification.requestedApiVersion());
        assertEquals(CloudIslandsApiContract.RUNTIME_API_VERSION, verification.runtimeApiVersion());
    }

    @Test
    void addonSnapshotDetectsRuntimeTooOldForRequestedApi() {
        Map<String, String> addonMetadata = new HashMap<>(ApiContractVerifier.addonCertificationMetadata(standardAddonMetadata(), CloudIslandsApiContract.metadata()));
        addonMetadata.put(ApiContractVerifier.REQUESTED_API_VERSION_KEY, "1.2.0");

        ApiContractVerification verification = ApiContractVerifier.verifyAddon("future-addon", "1.2.0", addonMetadata);

        assertFalse(verification.passed());
        assertEquals(ApiCompatibilityStatus.RUNTIME_TOO_OLD, verification.apiCompatibility().status());
        assertTrue(verification.failures().toString().contains("runtime-too-old"));
    }

    @Test
    void requirePassedThrowsWithActionableFailureSummary() {
        ApiContractVerification verification = ApiContractVerifier.verifyAddon("broken-addon", "1.0.0", Map.of());

        IllegalStateException error = assertThrows(IllegalStateException.class, verification::requirePassed);

        assertTrue(error.getMessage().contains("broken-addon"));
        assertTrue(error.getMessage().contains("missing-addon-metadata"));
        assertTrue(error.getMessage().contains("contract-metadata"));
    }

    private static Map<String, String> standardAddonMetadata() {
        return Map.of(
            "addon-standard-metadata-version", "1",
            "addon-packaging", "external-plugin",
            "addon-supported-packaging", "external-plugin,built-in-feature-pack,built-in-compatible",
            "addon-removal-safe", "true",
            "addon-removal-policy", "missing-disabled-or-removed-addon-must-not-block-core-island-create-route-save-restore",
            "addon-reconnect-policy", "reinstalled-addon-reconnects-preserved-addon-state-by-addon-id-and-island-uuid",
            "addon-config-gate-policy", "addon-enabled-and-feature-switches-control-runtime-components",
            "addon-event-delivery", "typed-cloud-event-callbacks-through-cloudislands-api",
            "addon-event-failure-policy", "addon-callback-exceptions-are-logged-and-isolated"
        );
    }
}
