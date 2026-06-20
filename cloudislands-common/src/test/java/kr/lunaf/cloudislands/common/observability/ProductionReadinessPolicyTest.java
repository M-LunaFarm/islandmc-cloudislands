package kr.lunaf.cloudislands.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
