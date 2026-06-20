package kr.lunaf.cloudislands.common.observability;

import java.util.List;

public final class ProductionReadinessPolicy {
    public static final String CONTRACT = "production-ga-requires-deploy-templates-config-migration-rolling-upgrade-multi-node-e2e-chaos-backup-restore-runbook";
    public static final String DEPLOYMENT_TEMPLATE_POLICY = "official-compose-and-helm-must-separate-core-redis-database-object-storage-velocity-lobby-and-island-paper-nodes";
    public static final String ROLLING_UPGRADE_POLICY = "upgrade-order-core-compatible-first-then-velocity-then-drained-paper-nodes-with-protocol-n-minus-one-checks";
    public static final String E2E_POLICY = "ga-gate-runs-multi-core-multi-paper-routing-save-restore-and-failover-scenarios";
    public static final String CHAOS_POLICY = "chaos-suite-covers-core-redis-db-object-storage-velocity-and-paper-node-failure-during-routing-and-save";
    public static final String BACKUP_RESTORE_POLICY = "backup-restore-drill-verifies-database-object-storage-bundles-snapshots-and-route-recovery-before-release";
    public static final String SUPPORT_BUNDLE_POLICY = "diagnostics-export-redacts-secrets-and-includes-version-config-node-storage-route-job-cache-and-recent-failure-state";

    private static final List<String> REQUIRED_GATES = List.of(
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
    );

    private ProductionReadinessPolicy() {
    }

    public static List<String> requiredGates() {
        return REQUIRED_GATES;
    }

    public static boolean requiredGate(String gate) {
        return gate != null && REQUIRED_GATES.contains(gate);
    }

    public static String requiredGateSummary() {
        return String.join(",", REQUIRED_GATES);
    }

    public static boolean drillMatrixComplete() {
        return ProductionGaDrillMatrix.gatesWithoutDrills(REQUIRED_GATES).isEmpty();
    }
}
