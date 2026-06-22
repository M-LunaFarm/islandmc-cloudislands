package kr.lunaf.cloudislands.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import kr.lunaf.cloudislands.common.observability.ProductionGaDrillMatrix;
import org.junit.jupiter.api.Test;

class ClusterSmokeVerifierTest {
    @Test
    void completeEvidenceCertifiesProductionGaClusterSmoke() {
        ClusterSmokeReport report = ClusterSmokeVerifier.verify(ClusterSmokeVerifier.completeEvidenceFixture());

        assertTrue(report.certified(), report.failures().toString());
        assertEquals(ClusterSmokeVerifier.CERTIFICATION_LEVEL, report.certificationLevel());
        assertTrue(ClusterSmokeVerifier.requiredFailureInjections().contains("ready-route-ticket-target-node-down"));
        assertEquals(10, ClusterSmokeVerifier.requiredProductionGaScenarios().size());
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
        assertTrue(report.missingComponents().contains("player-protocol-client"));
        assertThrows(IllegalStateException.class, report::requireCertified);
    }

    @Test
    void acceptsVirtualPlayerOrProtocolClientEvidenceForPlayerRouteCoverage() {
        ClusterSmokeEvidence virtualPlayer = ClusterSmokeEvidence.builder()
            .component("virtual-player")
            .build();
        ClusterSmokeEvidence protocolClient = ClusterSmokeEvidence.builder()
            .component("protocol-client")
            .build();

        assertTrue(virtualPlayer.hasComponent("player-protocol-client"));
        assertTrue(protocolClient.hasComponent("player-protocol-client"));
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
        assertEquals(
            java.util.List.of("idempotency-key-check", "audit-log-check", "event-replay-check"),
            report.missingEvidenceByGate().get("multi-core-e2e")
        );
        assertTrue(report.failures().stream().anyMatch(failure -> failure.contains("missing-evidence:")));
        assertTrue(report.missingEvidenceSummary().contains("multi-core-e2e=idempotency-key-check+audit-log-check+event-replay-check"));
        assertTrue(report.missingFailureInjections().contains("db-commit-event-publish-gap"));
        assertTrue(report.missingScenarioEvidence().containsKey("multi-core-activation-race"));
        assertTrue(report.missingScenarioFailureInjections().containsKey("ready-route-ticket-target-node-down"));
    }

    @Test
    void requiresEveryEditPlanProductionGaScenarioEvidence() {
        ClusterSmokeEvidence evidence = ClusterSmokeVerifier.completeEvidenceFixture();
        ClusterSmokeEvidence withoutReadyTargetNodeDown = ClusterSmokeEvidence.builder()
            .components(evidence.components())
            .failureInjections(evidence.failureInjections())
            .evidence("multi-core-e2e", evidence.evidenceByGate().get("multi-core-e2e"))
            .evidence("rolling-upgrade", evidence.evidenceByGate().get("rolling-upgrade"))
            .evidence("multi-paper-failover", java.util.List.of("two-island-paper-nodes", "save-interruption", "node-drain"))
            .evidence("chaos-test", evidence.evidenceByGate().get("chaos-test"))
            .evidence("backup-restore-drill", evidence.evidenceByGate().get("backup-restore-drill"))
            .build();

        ClusterSmokeReport report = ClusterSmokeVerifier.verify(withoutReadyTargetNodeDown);

        assertFalse(report.certified());
        assertEquals(
            java.util.List.of("migration-return-ticket", "fallback-server-check"),
            report.missingScenarioEvidence().get("ready-route-ticket-target-node-down")
        );
        assertTrue(report.failures().stream().anyMatch(failure -> failure.contains("missing-scenario-evidence:")));
        assertTrue(report.missingScenarioEvidenceSummary().contains("ready-route-ticket-target-node-down=migration-return-ticket+fallback-server-check"));
    }

    @Test
    void reportNamesEveryMissingGaEvidenceItemByGate() {
        ClusterSmokeReport report = ClusterSmokeVerifier.verify(
            ClusterSmokeEvidence.builder()
                .components(ClusterSmokeEvidence.REQUIRED_COMPONENTS)
                .evidence("backup-restore-drill", "db-backup")
                .evidence("backup-restore-drill", "object-storage-bundle")
                .build()
        );

        assertFalse(report.certified());
        assertEquals(
            java.util.List.of("manifest-checksum", "restore-activation", "route-recovery", "post-restore-audit"),
            report.missingEvidenceByGate().get("backup-restore-drill")
        );
        assertTrue(report.missingEvidenceSummary().contains("backup-restore-drill=manifest-checksum+restore-activation+route-recovery+post-restore-audit"));
    }

    @Test
    void cliReadsEvidenceJsonAndReportsMissingClusterSmokeItems() throws Exception {
        Path evidence = Files.createTempFile("cloudislands-cluster-evidence", ".json");
        Files.writeString(evidence, """
            {
              "components": ["core-1", "island-paper-1", "island-paper-2", "postgres", "redis", "object-storage"],
              "evidence": {
                "multi-paper-failover": ["two-island-paper-nodes", "save-interruption"],
                "backup-restore-drill": ["restore-activation", "route-recovery"]
              },
              "failureInjections": ["paper-save-kill", "snapshot-restore-node-failure"]
            }
            """);

        ClusterSmokeEvidence parsed = ClusterSmokeVerifierCli.readEvidence(evidence);
        ClusterSmokeReport report = ClusterSmokeVerifier.verify(parsed);
        String json = ClusterSmokeVerifierCli.reportJson(report);

        assertFalse(report.certified());
        assertTrue(parsed.hasComponent("island-paper-2"));
        assertTrue(report.missingComponents().contains("core-2"));
        assertTrue(report.missingComponents().contains("player-protocol-client"));
        assertTrue(report.missingEvidenceByGate().get("multi-paper-failover").contains("node-drain"));
        assertTrue(report.missingScenarioEvidence().containsKey("paper-bundle-save-crash"));
        assertTrue(json.contains("missingScenarioEvidence"));
        assertTrue(json.contains("missingScenarioFailureInjections"));
        assertTrue(json.contains("\"certified\":false"));
        assertTrue(json.contains("missingEvidenceByGate"));
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
