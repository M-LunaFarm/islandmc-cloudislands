package kr.lunaf.cloudislands.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix;
import org.junit.jupiter.api.Test;

class ClusterSmokeVerifierTest {
    @Test
    void completeEvidenceCertifiesProductionGaClusterSmoke() {
        ClusterSmokeReport report = ClusterSmokeVerifier.verify(ClusterSmokeVerifier.completeEvidenceFixture());

        assertTrue(report.certified(), report.failures().toString());
        assertEquals(ClusterSmokeVerifier.CERTIFICATION_LEVEL, report.certificationLevel());
        assertEquals(ProductionGaDrillMatrix.drills().stream().flatMap(drill -> drill.failureInjections().stream()).count(), ClusterSmokeVerifier.requiredFailureInjections().size());
    }

    @Test
    void requiresTwoCoreVelocityTwoPaperAndSharedStorageComponents() {
        ClusterSmokeReport report = ClusterSmokeVerifier.verify(
            ClusterSmokeEvidence.builder()
                .component("core-1")
                .component("velocity")
                .component("island-paper-1")
                .build()
        );

        assertFalse(report.certified());
        assertTrue(report.missingComponents().contains("core-2"));
        assertTrue(report.missingComponents().contains("lobby-paper"));
        assertTrue(report.missingComponents().contains("island-paper-2"));
        assertTrue(report.missingComponents().contains("postgres"));
        assertTrue(report.missingComponents().contains("redis"));
        assertTrue(report.missingComponents().contains("object-storage"));
        assertThrows(IllegalStateException.class, report::requireCertified);
    }

    @Test
    void incompleteEvidenceKeepsGateUncertifiedUntilEveryRequiredItemIsObserved() {
        ClusterSmokeEvidence evidence = ClusterSmokeEvidence.builder()
            .components(ClusterSmokeEvidence.REQUIRED_COMPONENTS)
            .evidence("multi-core-e2e", "two-core-instances")
            .evidence("multi-core-e2e", "fencing-token-check")
            .failureInjection("simultaneous-activation")
            .failureInjection("dual-admin-permission-edit")
            .build();

        ClusterSmokeReport report = ClusterSmokeVerifier.verify(evidence);

        assertFalse(report.certified());
        assertTrue(report.incompleteGates().contains("multi-core-e2e"));
        assertTrue(report.missingFailureInjections().contains("db-commit-event-publish-gap"));
    }

    @Test
    void builderNormalizesBlankAndDuplicateSignals() {
        ClusterSmokeEvidence evidence = ClusterSmokeEvidence.builder()
            .component("core-1")
            .component("core-1")
            .component(" ")
            .evidence("rolling-upgrade", "compatibility-matrix")
            .evidence("rolling-upgrade", " ")
            .failureInjection("old-paper-save-attempt")
            .failureInjection(" ")
            .build();

        assertTrue(evidence.hasComponent("core-1"));
        assertFalse(evidence.hasComponent(" "));
        assertTrue(evidence.hasEvidence("rolling-upgrade", "compatibility-matrix"));
        assertFalse(evidence.hasEvidence("rolling-upgrade", " "));
        assertTrue(evidence.injected("old-paper-save-attempt"));
    }
}
