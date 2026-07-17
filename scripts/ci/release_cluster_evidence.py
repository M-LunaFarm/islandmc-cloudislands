#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path


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


def require_any_marker(path: Path, markers: list[str]) -> None:
    if not path.is_file():
        raise RuntimeError(f"required smoke log is missing: {path}")
    text = path.read_text(encoding="utf-8", errors="replace")
    if not any(marker in text for marker in markers):
        raise RuntimeError(f"{path} is missing every accepted smoke marker: {markers}")


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


def build_release_evidence(core: dict, paper_log: Path, velocity_log: Path) -> dict:
    require_gate_evidence(core)
    require_markers(paper_log, ["CloudIslands Paper agent enabled", "CloudIslands agent role=LOBBY"])
    require_any_marker(paper_log, ["Done (", "Done preparing level"])
    require_markers(velocity_log, ["CloudIslands Velocity router enabled", "Listening on"])

    components = set(core.get("components", []))
    components.update(["velocity", "lobby-paper"])

    assertions = list(core.get("assertions", []))
    assertions.append({"name": "lobby-paper-boot-smoke", "result": "passed"})
    assertions.append({"name": "velocity-boot-smoke", "result": "passed"})
    assertions.append({"name": "partial-release-evidence-linked", "result": "passed"})

    artifacts = list(core.get("artifacts", []))
    artifacts.extend([artifact(paper_log), artifact(velocity_log)])

    release = dict(core)
    release["certificationScope"] = "partial-release-cluster-smoke"
    release["components"] = sorted(components)
    release["assertions"] = assertions
    release["artifacts"] = artifacts
    return release


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--core-evidence", required=True)
    parser.add_argument("--paper-log", required=True)
    parser.add_argument("--velocity-log", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    core_evidence = Path(args.core_evidence).resolve()
    paper_log = Path(args.paper_log).resolve()
    velocity_log = Path(args.velocity_log).resolve()
    out = Path(args.out).resolve()

    core = json.loads(core_evidence.read_text(encoding="utf-8"))
    release = build_release_evidence(core, paper_log, velocity_log)

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(release, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
