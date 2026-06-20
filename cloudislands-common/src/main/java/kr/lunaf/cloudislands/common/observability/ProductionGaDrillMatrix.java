package kr.lunaf.cloudislands.common.observability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProductionGaDrillMatrix {
    public static final String CONTRACT = "production-ga-drills-require-evidence-for-compose-helm-config-rolling-upgrade-multi-node-e2e-chaos-load-backup-restore-and-runbook";
    public static final String FAILURE_INJECTION_CONTRACT = "failure-injection-covers-core-paper-velocity-redis-db-object-storage-events-route-ticket-permission-and-snapshot-boundaries";

    private static final List<ProductionGaDrill> DRILLS = List.of(
        new ProductionGaDrill(
            "compose-template",
            "single-command local production stack with separated Core, Redis, database, object storage, Velocity, Lobby Paper, and Island Paper",
            List.of("compose-file", "secret-file-env", "healthchecks", "service-network-isolation"),
            List.of(),
            "operator can start the complete stack without embedding secrets in config"
        ),
        new ProductionGaDrill(
            "helm-chart",
            "Kubernetes install with external secret support and independently scalable Core and Paper workloads",
            List.of("chart", "values", "secretKeyRef", "service-definitions", "stateful-storage"),
            List.of(),
            "cluster deploys the same topology as Compose with secret references and persistent stores"
        ),
        new ProductionGaDrill(
            "config-migration",
            "v1 to v2 config migration validates conflicts, writes effective config, and preserves backup files",
            List.of("migration-report", "effective-config", "strict-validation", "secret-redaction", "rollback-backup"),
            List.of("duplicate-feature-gate-conflict", "unknown-key", "missing-secret"),
            "invalid config fails before runtime mutation while the previous config remains active"
        ),
        new ProductionGaDrill(
            "rolling-upgrade",
            "Core-compatible-first upgrade followed by Velocity and drained Paper nodes with protocol N-1 checks",
            List.of("compatibility-matrix", "drain-plan", "protocol-n-minus-one", "rollback-step", "post-upgrade-smoke"),
            List.of("old-paper-save-attempt", "mid-upgrade-route-ticket", "core-leader-change"),
            "players continue routing while stale agents are fenced from writes"
        ),
        new ProductionGaDrill(
            "multi-core-e2e",
            "two Core instances race island activation, permission edits, and route ticket creation against one authoritative store",
            List.of("two-core-instances", "fencing-token-check", "idempotency-key-check", "audit-log-check", "event-replay-check"),
            List.of("simultaneous-activation", "dual-admin-permission-edit", "db-commit-event-publish-gap"),
            "exactly one authoritative mutation wins and followers converge by replay"
        ),
        new ProductionGaDrill(
            "multi-paper-failover",
            "multiple Island Paper nodes handle save interruption, node drain, migration return, and visitor fallback",
            List.of("two-island-paper-nodes", "save-interruption", "node-drain", "migration-return-ticket", "fallback-server-check"),
            List.of("paper-save-kill", "snapshot-restore-node-failure", "visitor-kick-during-migration"),
            "island bundle is either durably saved or quarantined and players return to a safe server"
        ),
        new ProductionGaDrill(
            "chaos-test",
            "failure injection across Core, Redis, database, object storage, Velocity, Paper, events, and routing",
            List.of("fault-list", "blast-radius", "recovery-slo", "data-loss-check", "operator-alert"),
            List.of("core-kill", "redis-delay-duplicate-reorder", "db-unavailable", "object-storage-upload-after-db-fail", "velocity-kill-during-transfer"),
            "every injected fault has a bounded public failure mode and a diagnostic trail"
        ),
        new ProductionGaDrill(
            "load-test",
            "route, activation, permission, event, snapshot, and addon-state workload at configured node pool scale",
            List.of("route-throughput", "activation-latency", "event-lag", "snapshot-throughput", "resource-ceiling"),
            List.of("soft-full-node", "route-ticket-expiry-edge", "event-backlog"),
            "p95 and saturation metrics stay within the declared production envelope"
        ),
        new ProductionGaDrill(
            "backup-restore-drill",
            "database, object storage bundle, snapshot manifest, and route recovery restore rehearsal",
            List.of("db-backup", "object-storage-bundle", "manifest-checksum", "restore-activation", "route-recovery", "post-restore-audit"),
            List.of("object-storage-upload-after-db-commit-failure", "snapshot-restore-node-failure", "delete-backup-failure"),
            "restored island activates on a valid node and stale writes remain fenced"
        ),
        new ProductionGaDrill(
            "support-bundle",
            "diagnostics export packages version, node, route, cache, job, storage, config, and recent failure state",
            List.of("version", "node-state", "core-redis-db-storage", "route-ticket-state", "config-redaction", "recent-failures"),
            List.of("secret-in-config", "storage-health-failure", "route-ticket-stuck"),
            "operators can hand off a redacted support bundle without leaking secrets"
        ),
        new ProductionGaDrill(
            "operator-runbook",
            "runbook covers deploy, drain, rollback, backup restore, cache clear, support bundle, and emergency routing fallback",
            List.of("deploy", "drain", "rollback", "backup-restore", "cache-clear", "emergency-fallback"),
            List.of("core-unavailable", "redis-unavailable", "db-unavailable", "paper-node-lost", "velocity-transfer-failed"),
            "every GA gate has a documented operator action and verification command"
        )
    );

    private ProductionGaDrillMatrix() {
    }

    public static List<ProductionGaDrill> drills() {
        return DRILLS;
    }

    public static Optional<ProductionGaDrill> drill(String gate) {
        return DRILLS.stream().filter(drill -> drill.gate().equals(gate)).findFirst();
    }

    public static List<String> gatesWithoutDrills(List<String> gates) {
        if (gates == null) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (String gate : gates) {
            if (drill(gate).isEmpty()) {
                missing.add(gate);
            }
        }
        return List.copyOf(missing);
    }

    public static List<String> incompleteGates(Map<String, List<String>> evidenceByGate) {
        List<String> incomplete = new ArrayList<>();
        Map<String, List<String>> evidence = evidenceByGate == null ? Map.of() : evidenceByGate;
        for (ProductionGaDrill drill : DRILLS) {
            if (!drill.completeWith(evidence.get(drill.gate()))) {
                incomplete.add(drill.gate());
            }
        }
        return List.copyOf(incomplete);
    }

    public static String evidenceSummary() {
        List<String> summaries = new ArrayList<>();
        for (ProductionGaDrill drill : DRILLS) {
            summaries.add(drill.evidenceSummary());
        }
        return String.join(",", summaries);
    }

    public static String failureInjectionSummary() {
        List<String> failures = new ArrayList<>();
        for (ProductionGaDrill drill : DRILLS) {
            failures.addAll(drill.failureInjections());
        }
        return String.join(",", failures);
    }
}
