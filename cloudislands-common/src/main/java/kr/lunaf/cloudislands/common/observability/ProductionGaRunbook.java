package kr.lunaf.cloudislands.common.observability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProductionGaRunbook {
    public static final String CONTRACT = "operator-runbook-maps-every-production-ga-gate-to-action-verification-rollback-and-evidence";

    private static final Map<String, CommandSet> COMMANDS = Map.ofEntries(
        Map.entry("compose-template", new CommandSet(
            "docker compose -f deploy/compose/docker-compose.yml up -d",
            "docker compose -f deploy/compose/docker-compose.yml ps && curl -fsS http://127.0.0.1:18443/ready",
            "docker compose -f deploy/compose/docker-compose.yml down"
        )),
        Map.entry("helm-chart", new CommandSet(
            "helm upgrade --install cloudislands deploy/helm/cloudislands -f deploy/helm/cloudislands/values.yaml",
            "kubectl rollout status deploy/cloudislands-core && kubectl get statefulset,svc -l app.kubernetes.io/name=cloudislands",
            "helm rollback cloudislands || helm uninstall cloudislands"
        )),
        Map.entry("config-migration", new CommandSet(
            "ciadmin config validate --strict && ciadmin config migrate --dry-run",
            "ciadmin config effective && ciadmin diagnostics export",
            "restore config-v2 backup and run ciadmin reload"
        )),
        Map.entry("rolling-upgrade", new CommandSet(
            "ciadmin node drain <node> && upgrade core-compatible-first then velocity then drained paper nodes",
            "ciadmin status && ciadmin node list && run post-node-route-save-smoke",
            "rollback binary on drained node and ciadmin node undrain <node>"
        )),
        Map.entry("multi-core-e2e", new CommandSet(
            "start core-1 and core-2 against the same database, redis, and object storage",
            "scripts/ci/core_integration_smoke.py --core-bin <core-bin> --work-dir <dir>",
            "stop one core, replay events, and keep the surviving core as authority"
        )),
        Map.entry("multi-paper-failover", new CommandSet(
            "start lobby-paper plus island-paper-1 and island-paper-2 with shared bundle storage",
            "scripts/ci/papermc_smoke.py --project paper --version 1.21.11 --plugin <paper-jar> --work-dir <dir> --cache-dir <cache>",
            "ciadmin node drain <failed-node> && ciadmin node sweep <failed-node>"
        )),
        Map.entry("chaos-test", new CommandSet(
            "inject configured core, redis, db, object-storage, velocity, paper, event, and route faults",
            "ciadmin diagnostics export && check cloudislands route/storage/job/cache metrics",
            "remove fault, clear stuck route tickets, and replay events from the latest safe sequence"
        )),
        Map.entry("load-test", new CommandSet(
            "run route, activation, permission, event, snapshot, and addon-state workload at target node scale",
            "check p95 activation latency, event lag, route throughput, snapshot throughput, and resource ceilings",
            "stop load, drain saturated nodes, and restore limits to production defaults"
        )),
        Map.entry("backup-restore-drill", new CommandSet(
            "take database backup, verify object storage bundle, and request snapshot restore",
            "ciadmin island restore <island> <snapshot> && ciadmin island where <island>",
            "restore previous database backup and quarantine islands with checksum mismatch"
        )),
        Map.entry("support-bundle", new CommandSet(
            "ciadmin diagnostics export",
            "inspect exported version, node, route, cache, job, storage, config, and recent failure state",
            "redact or delete bundle, then rotate any credential that appeared in plain text"
        )),
        Map.entry("operator-runbook", new CommandSet(
            "review this ProductionGaRunbook before release approval",
            "verify every ProductionReadinessPolicy.requiredGates entry has an actionable runbook step",
            "block release and return to the failed gate owner"
        ))
    );

    private ProductionGaRunbook() {
    }

    public static List<ProductionRunbookStep> steps() {
        List<ProductionRunbookStep> steps = new ArrayList<>();
        for (ProductionGaDrill drill : ProductionGaDrillMatrix.drills()) {
            CommandSet commands = COMMANDS.getOrDefault(drill.gate(), CommandSet.empty());
            steps.add(new ProductionRunbookStep(
                drill.gate(),
                commands.actionCommand(),
                commands.verificationCommand(),
                commands.rollbackCommand(),
                drill.requiredEvidence(),
                drill.failureInjections()
            ));
        }
        return List.copyOf(steps);
    }

    public static Map<String, ProductionRunbookStep> stepsByGate() {
        LinkedHashMap<String, ProductionRunbookStep> steps = new LinkedHashMap<>();
        for (ProductionRunbookStep step : steps()) {
            steps.put(step.gate(), step);
        }
        return Map.copyOf(steps);
    }

    public static List<String> incompleteStepGates() {
        List<String> incomplete = new ArrayList<>();
        for (ProductionRunbookStep step : steps()) {
            if (!step.actionable()) {
                incomplete.add(step.gate());
            }
        }
        return List.copyOf(incomplete);
    }

    public static String summary() {
        List<String> summaries = new ArrayList<>();
        for (ProductionRunbookStep step : steps()) {
            summaries.add(step.summary());
        }
        return String.join("\n", summaries);
    }

    private record CommandSet(String actionCommand, String verificationCommand, String rollbackCommand) {
        static CommandSet empty() {
            return new CommandSet("", "", "");
        }
    }
}
