package kr.lunaf.cloudislands.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import kr.lunaf.cloudislands.api.CloudIslandsApiContract;
import kr.lunaf.cloudislands.api.addon.CloudIslandsAddon;
import org.junit.jupiter.api.Test;

class AddonCertificationMatrixTest {
    @Test
    void certifiesAStandardAddonAgainstTheRuntimeContract() {
        AddonCertificationReport report = AddonCertificationMatrix.certify(new MatrixAddon(), CloudIslandsApiContract.metadata());

        assertTrue(report.certified(), report.failureSummary().toString());
        assertEquals(AddonCertificationMatrix.CERTIFICATION_LEVEL, report.certificationLevel());
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("cloudislands-api-threading-policy")));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("addon-event-delivery")));
        assertTrue(report.checks().stream().anyMatch(check -> check.id().equals("addon-data-retention")));
    }

    @Test
    void rejectsUnsafeRemovalAndCoreLifecycleOwnership() {
        Map<String, String> metadata = new HashMap<>(new MatrixAddon().addonStandardMetadata());
        metadata.put("addon-removal-safe", "false");
        metadata.put("addon-core-lifecycle-owner", "true");

        AddonCertificationReport report = AddonCertificationMatrix.certify("unsafe-addon", metadata, CloudIslandsApiContract.metadata());

        assertFalse(report.certified());
        assertTrue(report.requiredFailures().stream().anyMatch(check -> check.id().equals("addon-removal-safe")));
        assertTrue(report.requiredFailures().stream().anyMatch(check -> check.id().equals("addon-core-lifecycle-owner")));
        assertThrows(IllegalStateException.class, report::requireCertified);
    }

    @Test
    void rejectsMissingEventIsolationAndStatePersistenceMetadata() {
        Map<String, String> metadata = new HashMap<>(new MatrixAddon().addonStandardMetadata());
        metadata.remove("addon-event-failure-policy");
        metadata.remove("addon-data-retention");

        AddonCertificationReport report = AddonCertificationMatrix.certify("missing-contract-addon", metadata, CloudIslandsApiContract.metadata());

        assertFalse(report.certified());
        assertTrue(report.requiredFailures().stream().anyMatch(check -> check.id().equals("addon-event-failure-policy")));
        assertTrue(report.requiredFailures().stream().anyMatch(check -> check.id().equals("addon-data-retention")));
    }

    @Test
    void rejectsFeatureProvidersWithoutPublishedKeys() {
        Map<String, String> metadata = new HashMap<>(new MatrixAddon().addonStandardMetadata());
        metadata.put("addon-placeholder-provider", "true");

        AddonCertificationReport report = AddonCertificationMatrix.certify("missing-provider-keys-addon", metadata, CloudIslandsApiContract.metadata());

        assertFalse(report.certified());
        assertTrue(report.requiredFailures().stream().anyMatch(check -> check.id().equals("addon-placeholder-keys")));
    }

    private static final class MatrixAddon implements CloudIslandsAddon {
        @Override
        public String addonId() {
            return "matrix-addon";
        }

        @Override
        public String addonDisplayName() {
            return "Matrix Addon";
        }

        @Override
        public String addonVersion() {
            return "1.0.0";
        }

        @Override
        public Map<String, String> addonMetadata() {
            return Map.of(
                "cloudislands-api-requested-version", CloudIslandsApiContract.RUNTIME_API_VERSION,
                "feature-dependencies", "route-events:addon-state"
            );
        }
    }
}
