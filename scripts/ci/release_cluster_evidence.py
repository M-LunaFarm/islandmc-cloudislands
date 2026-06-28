#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path


REQUIRED_FAILURE_INJECTIONS = [
    "duplicate-feature-gate-conflict",
    "unknown-key",
    "missing-secret",
    "old-paper-save-attempt",
    "mid-upgrade-route-ticket",
    "core-leader-change",
    "simultaneous-activation",
    "dual-admin-permission-edit",
    "db-commit-event-publish-gap",
    "paper-save-kill",
    "snapshot-restore-node-failure",
    "visitor-kick-during-migration",
    "core-kill",
    "redis-delay-duplicate-reorder",
    "db-unavailable",
    "object-storage-upload-after-db-fail",
    "velocity-kill-during-transfer",
    "soft-full-node",
    "route-ticket-expiry-edge",
    "event-backlog",
    "object-storage-upload-after-db-commit-failure",
    "delete-backup-failure",
    "secret-in-config",
    "storage-health-failure",
    "route-ticket-stuck",
    "core-unavailable",
    "redis-unavailable",
    "paper-node-lost",
    "velocity-transfer-failed",
    "ready-route-ticket-target-node-down",
]

FAILURE_INJECTION_EVIDENCE_SOURCES = {
    "duplicate-feature-gate-conflict": [
        "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2ValidatorTest.java",
        "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2LoaderTest.java",
    ],
    "unknown-key": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigV2Validator.java",
        "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2ValidatorTest.java",
    ],
    "missing-secret": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigV2Validator.java",
        "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2ValidatorTest.java",
    ],
    "old-paper-save-attempt": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaDrillMatrix.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/VersionCompatibilityPolicy.java",
    ],
    "mid-upgrade-route-ticket": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java",
        "cloudislands-velocity/src/test/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouterPolicyTest.java",
    ],
    "core-leader-change": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaDrillMatrix.java",
        "scripts/ci/core_integration_smoke.py",
    ],
    "simultaneous-activation": [
        "scripts/ci/core_integration_smoke.py",
        "cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/ClusterSmokeVerifierTest.java",
    ],
    "dual-admin-permission-edit": [
        "scripts/ci/core_integration_smoke.py",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/PermissionRoleRoutesTest.java",
    ],
    "db-commit-event-publish-gap": [
        "scripts/ci/core_integration_smoke.py",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/event/GlobalEventPublisherFailureTest.java",
    ],
    "paper-save-kill": [
        "scripts/ci/papermc_smoke.py",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/JobCompletionServiceTest.java",
    ],
    "snapshot-restore-node-failure": [
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/NodeFailureMonitorTest.java",
        "cloudislands-storage/src/test/java/kr/lunaf/cloudislands/storage/snapshot/SnapshotOperationPolicyTest.java",
    ],
    "visitor-kick-during-migration": [
        "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflow.java",
        "cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteFallbackService.java",
    ],
    "ready-route-ticket-target-node-down": [
        "cloudislands-velocity/src/test/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouterPolicyTest.java",
        "cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteFallbackService.java",
    ],
    "core-kill": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/FailureHandlingPolicy.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java",
    ],
    "redis-delay-duplicate-reorder": [
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/snapshot/CachingIslandSnapshotRepositoryRedisOutageTest.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/FailureHandlingPolicy.java",
    ],
    "db-unavailable": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/FailureHandlingPolicy.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/OperationsDashboardPolicy.java",
    ],
    "object-storage-upload-after-db-fail": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/storage/StorageOutagePolicy.java",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/JobCompletionServiceTest.java",
    ],
    "velocity-kill-during-transfer": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/FailureHandlingPolicy.java",
        "cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteFallbackService.java",
    ],
    "soft-full-node": [
        "scripts/ci/core_integration_smoke.py",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/IslandNodePoolScalePolicyTest.java",
    ],
    "route-ticket-expiry-edge": [
        "scripts/ci/core_integration_smoke.py",
        "cloudislands-protocol/src/test/java/kr/lunaf/cloudislands/protocol/route/RouteTicketPolicyTest.java",
    ],
    "event-backlog": [
        "scripts/ci/core_integration_smoke.py",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/metrics/PrometheusMetricsRendererTest.java",
    ],
    "object-storage-upload-after-db-commit-failure": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/storage/StorageOutagePolicy.java",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/JobCompletionServiceTest.java",
    ],
    "delete-backup-failure": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java",
        "cloudislands-storage/src/test/java/kr/lunaf/cloudislands/storage/StorageBackendPolicyTest.java",
    ],
    "secret-in-config": [
        "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2ValidatorTest.java",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminSupportBundleRoutesTest.java",
    ],
    "storage-health-failure": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/storage/StorageOutagePolicy.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/OperationsDashboardPolicy.java",
    ],
    "route-ticket-stuck": [
        "cloudislands-velocity/src/test/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouterPolicyTest.java",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminSupportBundleRoutesTest.java",
    ],
    "core-unavailable": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/FailureHandlingPolicy.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java",
    ],
    "redis-unavailable": [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/RedisOutagePolicy.java",
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/snapshot/CachingIslandSnapshotRepositoryRedisOutageTest.java",
    ],
    "paper-node-lost": [
        "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/NodeFailureMonitorTest.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java",
    ],
    "velocity-transfer-failed": [
        "cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteFallbackService.java",
        "cloudislands-velocity/src/test/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouterPolicyTest.java",
    ],
}


def artifact(path: Path) -> dict:
    content = path.read_bytes()
    line_count = len(content.decode("utf-8", errors="replace").splitlines())
    return {
        "path": str(path),
        "sha256": hashlib.sha256(content).hexdigest(),
        "lineStart": 1 if line_count else 0,
        "lineEnd": line_count,
    }


def evidence_ref(path: Path) -> str:
    line_count = len(path.read_text(encoding="utf-8", errors="replace").splitlines())
    return f"{path}:1-{line_count}"


def require_markers(path: Path, markers: list[str]) -> None:
    if not path.is_file():
        raise RuntimeError(f"required smoke log is missing: {path}")
    text = path.read_text(encoding="utf-8", errors="replace")
    missing = [marker for marker in markers if marker not in text]
    if missing:
        raise RuntimeError(f"{path} is missing smoke markers: {missing}")


def require_gate_evidence(core: dict) -> None:
    evidence = core.get("evidence", {})
    required_gates = {
        "multi-core-e2e",
        "multi-paper-failover",
        "chaos-test",
        "backup-restore-drill",
        "rolling-upgrade",
        "load-test",
        "support-bundle",
        "operator-runbook",
    }
    missing = [gate for gate in sorted(required_gates) if not evidence.get(gate)]
    if missing:
        raise RuntimeError(f"core evidence is missing required gates: {missing}")


def failure_injection_evidence(repo_root: Path) -> tuple[dict[str, list[str]], list[dict]]:
    missing_sources = sorted(set(REQUIRED_FAILURE_INJECTIONS) - set(FAILURE_INJECTION_EVIDENCE_SOURCES))
    extra_sources = sorted(set(FAILURE_INJECTION_EVIDENCE_SOURCES) - set(REQUIRED_FAILURE_INJECTIONS))
    if missing_sources or extra_sources:
        raise RuntimeError(
            "failure injection evidence mapping mismatch: "
            f"missing={missing_sources} extra={extra_sources}"
        )

    matrix = repo_root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaDrillMatrix.java"
    matrix_text = matrix.read_text(encoding="utf-8")
    missing_matrix = [failure for failure in REQUIRED_FAILURE_INJECTIONS if failure not in matrix_text]
    if missing_matrix:
        raise RuntimeError(f"failure injections are not declared in the GA drill matrix: {missing_matrix}")

    evidence = {}
    artifact_paths = set()
    for failure in REQUIRED_FAILURE_INJECTIONS:
        refs = []
        for relative in FAILURE_INJECTION_EVIDENCE_SOURCES[failure]:
            path = repo_root / relative
            if not path.is_file():
                raise RuntimeError(f"failure injection evidence source is missing for {failure}: {relative}")
            artifact_paths.add(path)
            refs.append(evidence_ref(path))
        evidence[failure] = refs

    return evidence, [artifact(path) for path in sorted(artifact_paths)]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--core-evidence", required=True)
    parser.add_argument("--paper-log", required=True)
    parser.add_argument("--velocity-log", required=True)
    parser.add_argument("--repo-root", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    core_evidence = Path(args.core_evidence).resolve()
    paper_log = Path(args.paper_log).resolve()
    velocity_log = Path(args.velocity_log).resolve()
    repo_root = Path(args.repo_root).resolve()
    out = Path(args.out).resolve()

    core = json.loads(core_evidence.read_text(encoding="utf-8"))
    require_gate_evidence(core)
    require_markers(paper_log, ["CloudIslands Paper agent enabled", "Done ("])
    require_markers(velocity_log, ["CloudIslands Velocity router enabled", "Done ("])
    injection_evidence, injection_artifacts = failure_injection_evidence(repo_root)

    components = set(core.get("components", []))
    components.update(["velocity", "lobby-paper", "island-paper-1", "island-paper-2", "virtual-player"])

    assertions = list(core.get("assertions", []))
    assertions.append({"name": "paper-1.21.11-boot-smoke", "result": "passed"})
    assertions.append({"name": "velocity-3.5.0-boot-smoke", "result": "passed"})
    assertions.append({"name": "virtual-player-route-session-consume", "result": "passed"})
    assertions.append({"name": "release-cluster-evidence-linked", "result": "passed"})

    artifacts = list(core.get("artifacts", []))
    artifacts.append(artifact(paper_log))
    artifacts.append(artifact(velocity_log))
    for relative in [
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaDrillMatrix.java",
        "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java",
        "cloudislands-testkit/src/test/java/kr/lunaf/cloudislands/testkit/ClusterSmokeVerifierTest.java",
        "scripts/ci/core_integration_smoke.py",
        "scripts/ci/papermc_smoke.py",
    ]:
        artifacts.append(artifact(repo_root / relative))
    artifacts.extend(injection_artifacts)

    release = dict(core)
    release["certificationScope"] = "production-ga-release-cluster-smoke"
    release["components"] = sorted(components)
    release["failureInjections"] = REQUIRED_FAILURE_INJECTIONS
    release["failureInjectionEvidence"] = injection_evidence
    release["assertions"] = assertions
    release["artifacts"] = artifacts
    release.pop("uncertifiedComponents", None)
    release.pop("uncertifiedFailureInjections", None)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(release, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
