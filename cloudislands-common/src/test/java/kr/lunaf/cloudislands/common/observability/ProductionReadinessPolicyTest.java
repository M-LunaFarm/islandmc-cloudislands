package kr.lunaf.cloudislands.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
