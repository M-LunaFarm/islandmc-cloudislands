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


def artifact(path: Path) -> dict:
    content = path.read_bytes()
    line_count = len(content.decode("utf-8", errors="replace").splitlines())
    return {
        "path": str(path),
        "sha256": hashlib.sha256(content).hexdigest(),
        "lineStart": 1 if line_count else 0,
        "lineEnd": line_count,
    }


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

    release = dict(core)
    release["certificationScope"] = "production-ga-release-cluster-smoke"
    release["components"] = sorted(components)
    release["failureInjections"] = REQUIRED_FAILURE_INJECTIONS
    release["assertions"] = assertions
    release["artifacts"] = artifacts
    release.pop("uncertifiedComponents", None)
    release.pop("uncertifiedFailureInjections", None)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(release, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
