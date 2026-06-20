package kr.lunaf.cloudislands.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductionReadinessPolicyTest {
    @Test
    void pinsProductionGaGatesFromEditPlan() {
        assertEquals(
            List.of(
                "compose-template",
                "helm-chart",
                "config-migration",
                "rolling-upgrade",
                "multi-core-e2e",
                "multi-paper-failover",
                "chaos-test",
                "load-test",
                "backup-restore-drill",
                "support-bundle",
                "operator-runbook"
            ),
            ProductionReadinessPolicy.requiredGates()
        );
        assertTrue(ProductionReadinessPolicy.requiredGate("compose-template"));
        assertTrue(ProductionReadinessPolicy.requiredGate("chaos-test"));
        assertFalse(ProductionReadinessPolicy.requiredGate("single-node-only"));
    }

    @Test
    void exposesOperationalContractStrings() {
        assertTrue(ProductionReadinessPolicy.CONTRACT.contains("production-ga"));
        assertTrue(ProductionReadinessPolicy.DEPLOYMENT_TEMPLATE_POLICY.contains("compose-and-helm"));
        assertTrue(ProductionReadinessPolicy.ROLLING_UPGRADE_POLICY.contains("protocol-n-minus-one"));
        assertTrue(ProductionReadinessPolicy.SUPPORT_BUNDLE_POLICY.contains("redacts-secrets"));
        assertTrue(ProductionReadinessPolicy.drillMatrixComplete());
    }

    @Test
    void productionGaDrillMatrixCoversEveryReadinessGate() {
        assertEquals(List.of(), ProductionGaDrillMatrix.gatesWithoutDrills(ProductionReadinessPolicy.requiredGates()));
        assertEquals(ProductionReadinessPolicy.requiredGates().size(), ProductionGaDrillMatrix.drills().size());
        assertTrue(ProductionGaDrillMatrix.CONTRACT.contains("production-ga-drills"));
        assertTrue(ProductionGaDrillMatrix.evidenceSummary().contains("backup-restore-drill=db-backup+object-storage-bundle+manifest-checksum+restore-activation+route-recovery+post-restore-audit"));
        assertTrue(ProductionGaDrillMatrix.evidenceSummary().contains("rolling-upgrade=compatibility-matrix+drain-plan+protocol-n-minus-one+rollback-step+post-upgrade-smoke"));
    }

    @Test
    void productionGaDrillMatrixPinsRequiredFailureInjectionScenarios() {
        String failures = ProductionGaDrillMatrix.failureInjectionSummary();

        assertTrue(failures.contains("simultaneous-activation"));
        assertTrue(failures.contains("paper-save-kill"));
        assertTrue(failures.contains("velocity-kill-during-transfer"));
        assertTrue(failures.contains("redis-delay-duplicate-reorder"));
        assertTrue(failures.contains("route-ticket-expiry-edge"));
        assertTrue(failures.contains("old-paper-save-attempt"));
        assertTrue(failures.contains("object-storage-upload-after-db-commit-failure"));
        assertTrue(failures.contains("db-commit-event-publish-gap"));
        assertTrue(failures.contains("dual-admin-permission-edit"));
        assertTrue(failures.contains("core-leader-change"));
        assertTrue(failures.contains("snapshot-restore-node-failure"));
    }

    @Test
    void productionGaDrillMatrixRequiresEvidenceBeforeCompletion() {
        Map<String, List<String>> evidence = new HashMap<>();
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            evidence.put(drill.gate(), drill.requiredEvidence());
        }

        assertEquals(List.of(), ProductionGaDrillMatrix.incompleteGates(evidence));

        evidence.put("backup-restore-drill", List.of("db-backup", "object-storage-bundle"));

        assertEquals(List.of("backup-restore-drill"), ProductionGaDrillMatrix.incompleteGates(evidence));
    }

    @Test
    void versionCompatibilityMatrixPinsPaperVelocityProtocolAndRollingUpgradeContracts() {
        assertEquals("1.21.11", VersionCompatibilityPolicy.SUPPORTED_PAPER_VERSION);
        assertEquals("21", VersionCompatibilityPolicy.SUPPORTED_JAVA_VERSION);
        assertEquals("3.5.0-SNAPSHOT", VersionCompatibilityPolicy.SUPPORTED_VELOCITY_VERSION);
        assertTrue(VersionCompatibilityPolicy.FOLIA_SUPPORT_POLICY.contains("not-supported"));
        assertTrue(VersionCompatibilityPolicy.MINECRAFT_UPDATE_POLICY.contains("paper-minor-updates"));
        assertTrue(VersionCompatibilityPolicy.PROTOCOL_CHANGE_POLICY.contains("n-minus-one"));
        assertTrue(VersionCompatibilityPolicy.MINOR_COMPATIBILITY_POLICY.contains("one-minor-window"));

        String matrix = VersionCompatibilityPolicy.matrixSummary();
        assertTrue(matrix.contains("core-1.1-to-paper-agent-1.0=compatible-with-write-fencing"));
        assertTrue(matrix.contains("core-1.1-to-velocity-1.0=compatible"));
        assertTrue(matrix.contains("protocol-schema-n-to-n-minus-one=compatible-for-one-minor-window"));
        assertTrue(matrix.contains("paper-agent-newer-than-core=blocked-for-authority-writes"));
        assertTrue(matrix.contains("folia-runtime=unsupported"));
        assertTrue(VersionCompatibilityPolicy.compatible("core-1.1-to-paper-agent-1.0"));
        assertFalse(VersionCompatibilityPolicy.compatible("paper-agent-newer-than-core"));

        assertEquals(
            "preflight-version-matrix>core-compatible-first>verify-core-schema-and-protocol-n-minus-one>upgrade-velocity>drain-one-paper-node>upgrade-drained-paper-node>post-node-route-save-smoke>repeat-paper-drain-upgrade>post-upgrade-multi-node-smoke",
            VersionCompatibilityPolicy.rollingUpgradeOrderSummary()
        );
    }

    @Test
    void shipsSeparateComposeAndHelmDeploymentTemplates() throws Exception {
        Path root = repositoryRoot();
        String compose = Files.readString(root.resolve("deploy/compose/docker-compose.yml"));
        assertTrue(compose.contains("postgres:"));
        assertTrue(compose.contains("redis:"));
        assertTrue(compose.contains("object-storage:"));
        assertTrue(compose.contains("core:"));
        assertTrue(compose.contains("velocity:"));
        assertTrue(compose.contains("lobby-paper:"));
        assertTrue(compose.contains("island-paper-1:"));
        assertTrue(compose.contains("_FILE"));

        assertTrue(Files.exists(root.resolve("deploy/helm/cloudislands/Chart.yaml")));
        String values = Files.readString(root.resolve("deploy/helm/cloudislands/values.yaml"));
        String workloads = Files.readString(root.resolve("deploy/helm/cloudislands/templates/workloads.yaml"));
        assertTrue(values.contains("existingSecret"));
        assertTrue(workloads.contains("name: cloudislands-core"));
        assertTrue(workloads.contains("name: cloudislands-velocity"));
        assertTrue(workloads.contains("name: cloudislands-lobby-paper"));
        assertTrue(workloads.contains("name: cloudislands-island-paper"));
        assertTrue(workloads.contains("name: cloudislands-postgres"));
        assertTrue(workloads.contains("name: cloudislands-redis"));
        assertTrue(workloads.contains("name: cloudislands-object-storage"));
        assertTrue(workloads.contains("secretKeyRef"));
    }

    private static Path repositoryRoot() {
        Path path = Path.of("").toAbsolutePath();
        while (path != null && !Files.exists(path.resolve("settings.gradle.kts"))) {
            path = path.getParent();
        }
        if (path == null) {
            throw new IllegalStateException("Repository root not found");
        }
        return path;
    }
}
