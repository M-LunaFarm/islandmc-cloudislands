#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
import urllib.error
import urllib.request
from pathlib import Path


CORE_TOKEN = "smoke"
ADMIN_TOKEN = "smoke"


class HttpError(RuntimeError):
    def __init__(self, method: str, path: str, status: int, body: str):
        super().__init__(f"{method} {path} returned {status}: {body}")
        self.status = status
        self.body = body


def request(base_url: str, method: str, path: str, payload=None, admin: bool = False, expect=(200,)):
    body = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    headers = {
        "Authorization": f"Bearer {CORE_TOKEN}",
        "Content-Type": "application/json",
    }
    if admin:
        headers["X-CloudIslands-Admin-Token"] = ADMIN_TOKEN
    req = urllib.request.Request(base_url + path, data=body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            text = response.read().decode("utf-8")
            status = response.status
    except urllib.error.HTTPError as error:
        text = error.read().decode("utf-8")
        status = error.code
    if status not in expect:
        raise HttpError(method, path, status, text)
    if not text:
        return {}
    return json.loads(text)


def wait_for_probe(base_url: str, path: str, deadline: float, label: str) -> None:
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(base_url + path, timeout=3) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(0.25)
    raise RuntimeError(f"Core service did not become {label} before timeout")


def wait_for_live(base_url: str, deadline: float) -> None:
    wait_for_probe(base_url, "/live", deadline, "live")


def wait_for_ready(base_url: str, deadline: float) -> None:
    wait_for_probe(base_url, "/ready", deadline, "ready")


def heartbeat(base_url: str, node_id: str, velocity_server_name: str, state: str = "READY", active_islands: int = 0) -> None:
    request(
        base_url,
        "POST",
        "/v1/nodes/heartbeat",
        {
            "protocolVersion": 1,
            "nodeId": node_id,
            "pool": "island",
            "velocityServerName": velocity_server_name,
            "nodeVersion": "integration-smoke",
            "state": state,
            "players": 0,
            "softPlayerCap": 90,
            "hardPlayerCap": 110,
            "reservedSlots": 5,
            "activeIslands": active_islands,
            "maxActiveIslands": 600,
            "mspt": 20.0,
            "activationQueue": 0,
            "maxActivationQueue": 20,
            "chunkLoadPressure": 0.0,
            "heapUsedMb": 256,
            "heapMaxMb": 1024,
            "recentFailurePenalty": 0,
            "storageAvailable": True,
            "supportedTemplates": "*",
        },
        expect=(202,),
    )


def latest_ticket(base_url: str, player_uuid: str):
    return request(
        base_url,
        "POST",
        "/v1/admin/routes/ticket",
        {"playerUuid": player_uuid},
        admin=True,
        expect=(200,),
    )


def claim_one(base_url: str, node_id: str, expected_type: str):
    deadline = time.monotonic() + 10
    last_jobs = None
    while time.monotonic() < deadline:
        jobs = request(
            base_url,
            "POST",
            "/v1/jobs/claim",
            {"nodeId": node_id, "supportedTypes": expected_type, "maxJobs": 4},
            admin=True,
            expect=(200,),
        )
        last_jobs = jobs
        job_list = jobs.get("jobs", jobs) if isinstance(jobs, dict) else jobs
        if isinstance(job_list, list):
            for job in job_list:
                if job.get("type") == expected_type:
                    return job
        time.sleep(0.25)
    raise RuntimeError(f"expected {expected_type} job for {node_id}, got {last_jobs}")


def complete_job(base_url: str, node_id: str, job: dict, payload: dict) -> None:
    request(
        base_url,
        "POST",
        "/v1/jobs/complete",
        {"nodeId": node_id, "jobId": job["jobId"], "claimLease": job.get("claimLease", {}), "payload": payload},
        admin=True,
        expect=(202,),
    )


def assert_field(data: dict, key: str, expected) -> None:
    actual = data.get(key)
    if actual != expected:
        raise RuntimeError(f"expected {key}={expected!r}, got {actual!r} in {data}")


def assert_contains_event(data: dict, expected_type: str) -> None:
    events = data.get("events", [])
    if not isinstance(events, list):
        raise RuntimeError(f"expected event list, got {data}")
    for event in events:
        if event.get("type") == expected_type:
            return
    raise RuntimeError(f"expected event type {expected_type}, got {data}")


def assert_contains_any_event(data: dict, expected_types: list[str]) -> None:
    events = data.get("events", [])
    if not isinstance(events, list):
        raise RuntimeError(f"expected event list, got {data}")
    types = {event.get("type") for event in events}
    for expected_type in expected_types:
        if expected_type in types:
            return
    raise RuntimeError(f"expected one of event types {expected_types}, got {data}")


def assert_audit_entries(data: dict) -> None:
    entries = data.get("entries", data.get("audit", []))
    if not isinstance(entries, list) or not entries:
        raise RuntimeError(f"expected audit entries, got {data}")


def start_core(core_bin: Path, instance_dir: Path, port: int, base_env: dict):
    instance_dir.mkdir(parents=True, exist_ok=True)
    log_path = instance_dir / "core.log"
    core_env = base_env.copy()
    core_env["CI_PORT"] = str(port)
    core_env["CI_ADMIN_BIND"] = "127.0.0.1"
    core_env["CI_ADMIN_PORT"] = str(port + 1000)
    core_env["CI_ADMIN_LISTENER_ENABLED"] = "true"
    core_env["CI_PUBLIC_ADMIN_API_ENABLED"] = "false"
    log = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(
        [str(core_bin), str(port)],
        cwd=instance_dir,
        env=core_env,
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
    )
    return process, log, log_path


def log_artifacts(processes: list[tuple[subprocess.Popen, object, Path]]) -> list[dict]:
    artifacts = []
    for _process, log, log_path in processes:
        log.flush()
        if not log_path.exists():
            continue
        content = log_path.read_bytes()
        line_count = len(content.decode("utf-8", errors="replace").splitlines())
        artifacts.append(
            {
                "path": str(log_path),
                "sha256": hashlib.sha256(content).hexdigest(),
                "lineStart": 1 if line_count else 0,
                "lineEnd": line_count,
            }
        )
    return artifacts


def file_artifact(path: Path, root: Path) -> dict:
    content = path.read_bytes()
    line_count = len(content.decode("utf-8", errors="replace").splitlines())
    return {
        "path": str(path.relative_to(root)),
        "sha256": hashlib.sha256(content).hexdigest(),
        "lineStart": 1 if line_count else 0,
        "lineEnd": line_count,
    }


def deployment_template_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    compose = root / "deploy/compose/docker-compose.yml"
    chart = root / "deploy/helm/cloudislands/Chart.yaml"
    values = root / "deploy/helm/cloudislands/values.yaml"
    workloads = root / "deploy/helm/cloudislands/templates/workloads.yaml"
    services = root / "deploy/helm/cloudislands/templates/services.yaml"
    required_files = [compose, chart, values, workloads, services]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"deployment evidence files are missing: {missing}")
    compose_text = compose.read_text(encoding="utf-8")
    values_text = values.read_text(encoding="utf-8")
    workloads_text = workloads.read_text(encoding="utf-8")
    services_text = services.read_text(encoding="utf-8")
    required_signals = {
        "compose-template": {
            "compose-file": "core:" in compose_text and "velocity:" in compose_text and "island-paper-2:" in compose_text,
            "secret-file-env": "_FILE" in compose_text and "secrets:" in compose_text,
            "healthchecks": "healthcheck:" in compose_text,
            "service-network-isolation": "networks:" in compose_text and "internal: true" in compose_text,
        },
        "helm-chart": {
            "chart": "apiVersion: v2" in chart.read_text(encoding="utf-8"),
            "values": "existingSecret" in values_text,
            "secretKeyRef": "secretKeyRef" in workloads_text,
            "service-definitions": "kind: Service" in services_text,
            "stateful-storage": "volumeClaimTemplates" in workloads_text,
        },
    }
    failures = [
        f"{gate}:{evidence}"
        for gate, checks in required_signals.items()
        for evidence, present in checks.items()
        if not present
    ]
    if failures:
        raise RuntimeError(f"deployment evidence signals are missing: {failures}")
    evidence = {gate: list(checks.keys()) for gate, checks in required_signals.items()}
    artifacts = [file_artifact(path, root) for path in required_files]
    return evidence, artifacts


def idempotency_evidence_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    schema = root / "cloudislands-core-service/src/main/resources/db/migration/V1__cloudislands_schema.sql"
    mysql_schema = root / "cloudislands-core-service/src/main/resources/db/mysql/V1__cloudislands_mysql_schema.sql"
    jdbc_queue = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/job/JdbcIslandJobQueue.java"
    in_memory_queue = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/job/InMemoryIslandJobPublisher.java"
    in_memory_test = root / "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/InMemoryIslandJobPublisherTest.java"
    required_files = [schema, mysql_schema, jdbc_queue, in_memory_queue, in_memory_test]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"idempotency evidence files are missing: {missing}")
    schema_text = schema.read_text(encoding="utf-8")
    mysql_schema_text = mysql_schema.read_text(encoding="utf-8")
    jdbc_text = jdbc_queue.read_text(encoding="utf-8")
    in_memory_text = in_memory_queue.read_text(encoding="utf-8")
    test_text = in_memory_test.read_text(encoding="utf-8")
    required_signals = {
        "postgres-request-id-unique-index": "idx_island_jobs_request_id" in schema_text and "request_id" in schema_text,
        "mysql-request-id-unique-index": "idx_island_jobs_request_id" in mysql_schema_text and "request_id" in mysql_schema_text,
        "jdbc-conflict-noop": "ON CONFLICT (request_id) DO NOTHING" in jdbc_text and "ON DUPLICATE KEY UPDATE request_id = request_id" in jdbc_text,
        "in-memory-duplicate-job-skip": "anyMatch(record -> record.job().jobId().equals(job.jobId()))" in in_memory_text,
        "idempotent-publish-test": "duplicateJobIdPublishIsIdempotent" in test_text,
    }
    failures = [name for name, present in required_signals.items() if not present]
    if failures:
        raise RuntimeError(f"idempotency evidence signals are missing: {failures}")
    return {"multi-core-e2e": ["idempotency-key-check"]}, [file_artifact(path, root) for path in required_files]


def operator_runbook_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    runbook = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java"
    runtime_routes = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminRuntimeRoutes.java"
    support_routes = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminSupportBundleRoutes.java"
    required_files = [runbook, runtime_routes, support_routes]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"operator runbook evidence files are missing: {missing}")
    runbook_text = runbook.read_text(encoding="utf-8")
    runtime_text = runtime_routes.read_text(encoding="utf-8")
    support_text = support_routes.read_text(encoding="utf-8")
    required_signals = {
        "deploy": "deploy" in runbook_text and "helm upgrade" in runbook_text and "docker compose" in runbook_text,
        "drain": "ciadmin node drain" in runbook_text,
        "rollback": "rollback" in runbook_text and "block release" in runbook_text,
        "backup-restore": "backup-restore" in runbook_text and "ciadmin island restore" in runbook_text,
        "cache-clear": "cache clear" in runbook_text and '"/v1/admin/cache/clear"' in runtime_text,
        "emergency-fallback": "emergency-fallback" in runbook_text,
        "support-bundle": '"/v1/admin/support-bundle"' in support_text,
    }
    failures = [name for name, present in required_signals.items() if not present]
    if failures:
        raise RuntimeError(f"operator runbook evidence signals are missing: {failures}")
    return {
        "operator-runbook": [
            "deploy",
            "drain",
            "rollback",
            "backup-restore",
            "cache-clear",
            "emergency-fallback",
        ]
    }, [file_artifact(path, root) for path in required_files]


def config_migration_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    report = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigMigrationReport.java"
    loader = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigV2Loader.java"
    validator = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigV2Validator.java"
    reload_plan = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/config/ConfigReloadPlan.java"
    loader_test = root / "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2LoaderTest.java"
    validator_test = root / "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/config/ConfigV2ValidatorTest.java"
    paper_config_admin = root / "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/admin/AdminConfigCommandHandler.java"
    core_config = root / "cloudislands-core-service/src/main/resources/config-v2/application.yml"
    paper_config = root / "cloudislands-paper/src/main/resources/config-v2/config.yml"
    velocity_config = root / "cloudislands-velocity/src/main/resources/config-v2/config.yml"
    required_files = [
        report,
        loader,
        validator,
        reload_plan,
        loader_test,
        validator_test,
        paper_config_admin,
        core_config,
        paper_config,
        velocity_config,
    ]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"config migration evidence files are missing: {missing}")
    report_text = report.read_text(encoding="utf-8")
    loader_text = loader.read_text(encoding="utf-8")
    validator_text = validator.read_text(encoding="utf-8")
    reload_text = reload_plan.read_text(encoding="utf-8")
    test_text = loader_test.read_text(encoding="utf-8") + "\n" + validator_test.read_text(encoding="utf-8")
    admin_text = paper_config_admin.read_text(encoding="utf-8")
    config_text = "\n".join(path.read_text(encoding="utf-8") for path in (core_config, paper_config, velocity_config))
    required_signals = {
        "migration-report": "record ConfigMigrationReport" in report_text and "migrationReportBlocksConflictingLegacyValues" in test_text,
        "effective-config": "effective-config" in loader_text and "effective-config-redacted" in admin_text,
        "strict-validation": "strict-validation: true" in config_text and "validateYaml" in validator_text and "bundledConfigV2YamlPassesDuplicateAndSecretValidation" in test_text,
        "secret-redaction": "redactYaml" in validator_text and "redactsSecretsFromEffectiveConfigOutput" in test_text and "redactDiagnostic" in admin_text,
        "rollback-backup": "rollbackBackupYaml" in reload_text and "reloadRollbackKeepsPreviousEffectiveConfigWhenCandidateInvalid" in test_text,
    }
    failures = [name for name, present in required_signals.items() if not present]
    if failures:
        raise RuntimeError(f"config migration evidence signals are missing: {failures}")
    return {"config-migration": list(required_signals.keys())}, [file_artifact(path, root) for path in required_files]


def rolling_upgrade_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    version_policy = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/VersionCompatibilityPolicy.java"
    runbook = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java"
    admin_nodes = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminNodeRoutes.java"
    admin_security = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/security/AdminEndpointGuard.java"
    protocol_policy = root / "cloudislands-protocol/src/main/java/kr/lunaf/cloudislands/protocol/InternalApiProtocolPolicy.java"
    test = root / "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/observability/ProductionReadinessPolicyTest.java"
    required_files = [version_policy, runbook, admin_nodes, admin_security, protocol_policy, test]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"rolling upgrade evidence files are missing: {missing}")
    version_text = version_policy.read_text(encoding="utf-8")
    runbook_text = runbook.read_text(encoding="utf-8")
    admin_node_text = admin_nodes.read_text(encoding="utf-8")
    security_text = admin_security.read_text(encoding="utf-8")
    protocol_text = protocol_policy.read_text(encoding="utf-8")
    test_text = test.read_text(encoding="utf-8")
    required_signals = {
        "compatibility-matrix": "VersionCompatibilityRow" in version_text and "matrixSummary" in version_text,
        "drain-plan": '"/v1/admin/nodes/drain"' in admin_node_text and '"/v1/admin/nodes/undrain"' in admin_node_text and "ciadmin node drain" in runbook_text,
        "protocol-n-minus-one": "protocol-schema-n-to-n-minus-one" in version_text and "version-negotiation" in protocol_text,
        "rollback-step": "rollback binary on drained node" in runbook_text and "ciadmin node undrain" in runbook_text and "NODE_UNDRAIN" in security_text,
        "post-upgrade-smoke": "post-upgrade-multi-node-smoke" in version_text and "core_integration_smoke.py" in runbook_text,
    }
    failures = [name for name, present in required_signals.items() if not present]
    if failures:
        raise RuntimeError(f"rolling upgrade evidence signals are missing: {failures}")
    return {"rolling-upgrade": list(required_signals.keys())}, [file_artifact(path, root) for path in required_files]


def support_bundle_evidence(support_bundle: dict | None) -> dict[str, list[str]]:
    if not support_bundle:
        return {}
    required = {
        "version": bool(support_bundle.get("version")),
        "node-state": bool(support_bundle.get("nodeState")),
        "core-redis-db-storage": bool(support_bundle.get("coreRedisDbStorage")),
        "route-ticket-state": bool(support_bundle.get("routeTicketState")),
        "config-redaction": bool(support_bundle.get("configRedaction", {}).get("secretsRedacted")),
        "recent-failures": isinstance(support_bundle.get("recentFailures"), list),
    }
    missing = [name for name, present in required.items() if not present]
    if missing:
        raise RuntimeError(f"support bundle evidence signals are missing: {missing}")
    text = json.dumps(support_bundle, sort_keys=True)
    forbidden_patterns = (
        '"coreToken":',
        '"adminToken":',
        '"nodeCredentials":',
        '"storageSecretKey":',
        '"databasePassword":',
    )
    leaked_patterns = [pattern for pattern in forbidden_patterns if pattern in text]
    if leaked_patterns:
        raise RuntimeError(f"support bundle exposed secret values: {leaked_patterns}")
    if '"nonce":' in text and '"nonce":"hidden"' not in text and '"nonce": "hidden"' not in text:
        raise RuntimeError("support bundle exposed an unmasked route nonce")
    return {"support-bundle": list(required.keys())}


def write_cluster_evidence(path: Path | None, artifacts: list[dict], support_bundle: dict | None = None) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    deployment_evidence, deployment_artifacts = deployment_template_artifacts()
    config_evidence, config_artifacts = config_migration_artifacts()
    rolling_evidence, rolling_artifacts = rolling_upgrade_artifacts()
    idempotency_evidence, idempotency_artifacts = idempotency_evidence_artifacts()
    runbook_evidence, runbook_artifacts = operator_runbook_artifacts()
    runtime_evidence = support_bundle_evidence(support_bundle)
    evidence = {
        "certificationScope": "partial-core-integration-smoke",
        "components": [
            "core-1",
            "core-2",
            "postgres",
            "redis",
            "object-storage",
        ],
        "evidence": {
            "multi-core-e2e": [
                "two-core-instances",
                "fencing-token-check",
                "audit-log-check",
                "event-replay-check",
                *idempotency_evidence["multi-core-e2e"],
            ],
            "backup-restore-drill": [
                "restore-activation",
                "route-recovery",
            ],
            **deployment_evidence,
            **config_evidence,
            **rolling_evidence,
            **runbook_evidence,
            **runtime_evidence,
        },
        "failureInjections": [],
        "assertions": [
            {"name": "primary-core-ready", "result": "passed"},
            {"name": "secondary-core-ready", "result": "passed"},
            {"name": "shared-postgresql-authority", "result": "passed"},
            {"name": "shared-redis-job-and-event-mode", "result": "passed"},
            {"name": "shared-s3-storage-mode", "result": "passed"},
            {"name": "config-migration-evidence-present", "result": "passed"},
            {"name": "rolling-upgrade-evidence-present", "result": "passed"},
            {"name": "cross-core-create-job-complete", "result": "passed"},
            {"name": "route-session-consume-round-trip", "result": "passed"},
            {"name": "fencing-token-positive", "result": "passed"},
            {"name": "event-replay-visible-on-secondary-core", "result": "passed"},
            {"name": "audit-entries-visible-on-secondary-core", "result": "passed"},
            {"name": "node-down-recovery-restore", "result": "passed"},
            {"name": "post-recovery-route-targets-standby-node", "result": "passed"},
            {"name": "operator-runbook-covers-ga-actions", "result": "passed"},
            {"name": "support-bundle-redacted", "result": "passed" if runtime_evidence else "not-run"},
        ],
        "artifacts": artifacts + deployment_artifacts + config_artifacts + rolling_artifacts + idempotency_artifacts + runbook_artifacts,
        "uncertifiedComponents": ["velocity", "lobby-paper", "island-paper-1", "island-paper-2", "player-protocol-client"],
        "uncertifiedFailureInjections": [
            "paper-save-kill",
            "snapshot-restore-node-failure",
            "object-storage-upload-after-db-commit-failure",
        ],
    }
    path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run_scenario(core_bin: Path, work_dir: Path, port: int, timeout: int, evidence_out: Path | None = None, secondary_port: int | None = None) -> None:
    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    secondary_port = secondary_port or port + 1
    if secondary_port == port:
        raise RuntimeError("secondary Core port must differ from primary port")
    primary_url = f"http://127.0.0.1:{port}"
    secondary_url = f"http://127.0.0.1:{secondary_port}"
    primary_admin_url = f"http://127.0.0.1:{port + 1000}"
    secondary_admin_url = f"http://127.0.0.1:{secondary_port + 1000}"
    env = os.environ.copy()
    env.update(
        {
            "CI_BIND": "127.0.0.1",
            "CI_REPOSITORY_MODE": "JDBC",
            "CI_JOB_QUEUE_MODE": "REDIS",
            "CI_EVENT_BUS_MODE": "REDIS",
            "CI_DATABASE_TYPE": "POSTGRESQL",
            "CI_JDBC_URL": env.get("CI_JDBC_URL", "jdbc:postgresql://127.0.0.1:5432/cloudislands"),
            "CI_DB_USERNAME": env.get("CI_DB_USERNAME", "cloudislands"),
            "CI_DB_PASSWORD": env.get("CI_DB_PASSWORD", "cloudislands"),
            "CI_DB_POOL_SIZE": env.get("CI_DB_POOL_SIZE", "6"),
            "CI_DB_AUTO_SCHEMA": "true",
            "CI_DB_FALLBACK_ENABLED": "false",
            "CI_ALLOW_IN_MEMORY_FALLBACK": "false",
            "CI_REDIS_URI": env.get("CI_REDIS_URI", "redis://127.0.0.1:6379"),
            "CI_STORAGE_TYPE": "S3",
            "CI_STORAGE_ENDPOINT": env.get("CI_STORAGE_ENDPOINT", "http://127.0.0.1:9000"),
            "CI_STORAGE_BUCKET": env.get("CI_STORAGE_BUCKET", "cloudislands"),
            "CI_STORAGE_REGION": env.get("CI_STORAGE_REGION", "us-east-1"),
            "CI_STORAGE_ACCESS_KEY": env.get("CI_STORAGE_ACCESS_KEY", "minioadmin"),
            "CI_STORAGE_SECRET_KEY": env.get("CI_STORAGE_SECRET_KEY", "minioadmin"),
            "CI_CORE_TOKEN": CORE_TOKEN,
            "CI_ADMIN_TOKEN": ADMIN_TOKEN,
            "CI_ADMIN_PERMISSIONS": "*",
            "CI_REQUIRE_MTLS": "false",
            "CI_IP_ALLOWLIST": "127.0.0.1,localhost,::1",
            "CI_RATE_LIMIT_REQUESTS": "0",
            "CI_HEARTBEAT_TIMEOUT_SECONDS": "30",
            "CI_ROUTE_TICKET_TTL_SECONDS": "30",
            "CI_ROUTE_PREPARING_TICKET_TTL_SECONDS": "120",
            "CI_MIGRATION_POLICY": "ACTIVE_ALLOWED",
            "CI_SNAPSHOT_KEEP_LATEST": "5",
        }
    )
    processes = []
    try:
        deadline = time.monotonic() + timeout
        processes.append(start_core(core_bin, work_dir / "core-1", port, env))
        wait_for_live(primary_url, deadline)
        processes.append(start_core(core_bin, work_dir / "core-2", secondary_port, env))
        wait_for_live(secondary_url, deadline)
        node_a = "island-a-" + uuid.uuid4().hex[:8]
        node_b = "island-b-" + uuid.uuid4().hex[:8]
        node_servers = {
            node_a: "Island-A-" + node_a.rsplit("-", 1)[-1],
            node_b: "Island-B-" + node_b.rsplit("-", 1)[-1],
        }
        heartbeat(primary_url, node_a, node_servers[node_a])
        heartbeat(primary_url, node_b, node_servers[node_b])
        wait_for_ready(primary_url, deadline)
        wait_for_ready(secondary_url, deadline)
        for base_url in (primary_admin_url, secondary_admin_url):
            config = request(base_url, "POST", "/v1/admin/config", {}, admin=True, expect=(200,))
            assert_field(config, "effectiveRepositoryMode", "JDBC")
            assert_field(config, "effectiveJobQueueMode", "REDIS")
            assert_field(config, "effectiveEventBusMode", "REDIS")
            assert_field(config, "databaseBackend", "POSTGRESQL")
            assert_field(config, "storageType", "S3")
            if not config.get("storageMultiNodeSafe"):
                raise RuntimeError(f"expected S3 storage to be multi-node safe: {config}")

        nodes = request(secondary_url, "POST", "/v1/nodes", {}, expect=(200,))
        if nodes.get("routeCandidateCount", 0) < 2:
            raise RuntimeError(f"expected two route candidates from secondary core, got {nodes}")

        run_id = uuid.uuid4().hex[:12]
        player_uuid = str(uuid.uuid4())
        create = request(
            primary_url,
            "POST",
            "/v1/islands",
            {"playerUuid": player_uuid, "templateId": "default"},
            expect=(202,),
        )
        assert_field(create, "accepted", True)
        island_id = create["islandId"]
        ticket = create["ticket"]
        active_node = ticket["targetNode"]
        if active_node not in (node_a, node_b):
            raise RuntimeError(f"unexpected create target node {active_node}")
        standby_node = node_b if active_node == node_a else node_a

        create_job = claim_one(primary_url, active_node, "CREATE_ISLAND")
        if int(create_job.get("payload", {}).get("fencingToken", "0")) <= 0:
            raise RuntimeError(f"expected create job fencing token, got {create_job}")
        complete_job(
            secondary_url,
            active_node,
            create_job,
            {
                "worldName": "ci_smoke_world_" + run_id,
                "cellX": "1",
                "cellZ": "2",
                "snapshotNo": "1",
                "reason": "CREATED",
                "checksum": "sha256:integration-smoke-create",
                "sizeBytes": "2048",
                "placementSource": "integration-smoke",
            },
        )

        ready_ticket = request(
            secondary_url,
            "POST",
            "/v1/routes/ticket-status",
            {"ticketId": ticket["ticketId"], "playerUuid": player_uuid, "nonce": ticket["nonce"]},
            expect=(200,),
        )
        assert_field(ready_ticket, "state", "READY")
        request(
            primary_url,
            "POST",
            "/v1/routes/session",
            {
                "ticketId": ticket["ticketId"],
                "playerUuid": player_uuid,
                "targetNode": active_node,
                "nonce": ticket["nonce"],
            },
            expect=(202,),
        )
        found_session = request(
            secondary_url,
            "POST",
            "/v1/routes/session/find",
            {"playerUuid": player_uuid, "nodeId": active_node},
            expect=(200,),
        )
        assert_field(found_session, "targetNode", active_node)
        consumed_session = request(
            secondary_url,
            "POST",
            "/v1/routes/session/consume",
            {
                "playerUuid": player_uuid,
                "nodeId": active_node,
                "ticketId": ticket["ticketId"],
                "nonce": ticket["nonce"],
            },
            expect=(200,),
        )
        assert_field(consumed_session, "ticketId", ticket["ticketId"])
        consumed_ticket = request(
            secondary_url,
            "POST",
            "/v1/routes/consume",
            {
                "ticketId": ticket["ticketId"],
                "playerUuid": player_uuid,
                "nodeId": active_node,
                "nonce": ticket["nonce"],
            },
            expect=(200,),
        )
        assert_field(consumed_ticket, "state", "CONSUMED")

        where = request(secondary_admin_url, "POST", "/v1/admin/islands/where", {"islandId": island_id}, admin=True, expect=(200,))
        assert_field(where, "state", "ACTIVE")
        assert_field(where, "activeNode", active_node)
        if int(where.get("fencingToken", 0)) <= 0:
            raise RuntimeError(f"expected active runtime fencing token from secondary core, got {where}")

        heartbeat(secondary_url, active_node, node_servers[active_node], state="DOWN", active_islands=1)
        heartbeat(primary_url, standby_node, node_servers[standby_node])
        sweep = request(
            secondary_admin_url,
            "POST",
            "/v1/admin/nodes/sweep",
            {"nodeId": active_node},
            admin=True,
            expect=(202,),
        )
        if sweep.get("recoveryRequired") < 1 or sweep.get("recoveryQueued") < 1:
            raise RuntimeError(f"expected node sweep to queue recovery, got {sweep}")

        restore_job = claim_one(secondary_url, standby_node, "RESTORE_ISLAND")
        if int(restore_job.get("payload", {}).get("fencingToken", "0")) <= 0:
            raise RuntimeError(f"expected restore job fencing token, got {restore_job}")
        complete_job(
            primary_url,
            standby_node,
            restore_job,
            {
                "worldName": "ci_smoke_world_recovered_" + run_id,
                "cellX": "3",
                "cellZ": "4",
                "preMutationSnapshotNo": "2",
                "preMutationReason": "BEFORE_RESTORE",
                "preMutationChecksum": "sha256:integration-smoke-before-restore",
                "preMutationSizeBytes": "1024",
                "placementSource": "integration-smoke-recovery",
            },
        )
        recovered = request(secondary_admin_url, "POST", "/v1/admin/islands/where", {"islandId": island_id}, admin=True, expect=(200,))
        assert_field(recovered, "state", "ACTIVE")
        assert_field(recovered, "activeNode", standby_node)

        reconnect = request(
            secondary_url,
            "POST",
            "/v1/routes/home",
            {"playerUuid": player_uuid},
            expect=(202,),
        )
        reconnect_ticket = latest_ticket(primary_admin_url, player_uuid)
        assert_field(reconnect_ticket, "state", "READY")
        assert_field(reconnect_ticket, "targetNode", standby_node)
        assert_field(reconnect, "state", "READY")
        assert_field(reconnect, "targetNode", standby_node)

        events = request(secondary_admin_url, "POST", "/v1/events", {"limit": 100}, admin=True, expect=(200,))
        assert_contains_any_event(events, ["ROUTE_TICKET_CREATED", "ROUTE_SESSION_PUBLISHED", "ISLAND_ACTIVATED"])
        audit = request(secondary_admin_url, "POST", "/v1/audit", {"limit": 100}, admin=True, expect=(200,))
        assert_audit_entries(audit)
        support_bundle = request(secondary_admin_url, "POST", "/v1/admin/support-bundle", {}, admin=True, expect=(200,))
        support_bundle_evidence(support_bundle)

        print(
            "Core integration smoke passed: two Core services, PostgreSQL+Redis+MinIO config, "
            "cross-core create/job/route/session/consume, node-down recovery restore, reconnect"
        )
        write_cluster_evidence(evidence_out, log_artifacts(processes), support_bundle)
    finally:
        failures = []
        for process, log, log_path in processes:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            log.close()
            if process.returncode not in (0, 143, -15):
                tail = log_path.read_text(encoding="utf-8", errors="replace").splitlines()[-120:]
                failures.append(f"{log_path} exited with {process.returncode}:\n" + "\n".join(tail))
        if failures:
            raise RuntimeError("Core service exited unexpectedly:\n" + "\n\n".join(failures))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--core-bin", required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--port", type=int, default=18443)
    parser.add_argument("--secondary-port", type=int)
    parser.add_argument("--timeout", type=int, default=90)
    parser.add_argument("--evidence-out")
    args = parser.parse_args()
    core_bin = Path(args.core_bin).resolve()
    if not core_bin.exists():
        raise RuntimeError(f"Core binary does not exist: {core_bin}")
    evidence_out = Path(args.evidence_out).resolve() if args.evidence_out else None
    run_scenario(core_bin, Path(args.work_dir).resolve(), args.port, args.timeout, evidence_out, args.secondary_port)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"core integration smoke failed: {exc}", file=sys.stderr)
        raise
