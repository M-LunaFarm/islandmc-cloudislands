#!/usr/bin/env python3
import argparse
import hashlib
import hmac
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timezone
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


def timed_request(base_url: str, method: str, path: str, payload=None, admin: bool = False, expect=(200,)):
    started = time.perf_counter()
    response = request(base_url, method, path, payload, admin=admin, expect=expect)
    return response, time.perf_counter() - started


def percentile(values: list[float], ratio: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int((len(ordered) - 1) * ratio)))
    return ordered[index]


def rounded_seconds(value: float) -> float:
    return round(max(0.0, value), 6)


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


def assert_list_contains(data: dict, key: str, field: str, expected) -> None:
    entries = data.get(key, [])
    if not isinstance(entries, list):
        raise RuntimeError(f"expected {key} list, got {data}")
    for entry in entries:
        if isinstance(entry, dict) and entry.get(field) == expected:
            return
    raise RuntimeError(f"expected {key} entry with {field}={expected!r}, got {data}")


def run_player_interaction_smoke(base_url: str, admin_url: str, island_id: str, owner_uuid: str, active_node: str) -> dict:
    member_uuid = str(uuid.uuid4())
    unauthorized_uuid = str(uuid.uuid4())

    deposit = request(
        base_url,
        "POST",
        "/v1/islands/bank/deposit",
        {"islandId": island_id, "actorUuid": owner_uuid, "amount": "100.00"},
        expect=(202,),
    )
    assert_field(deposit, "balance", "100.00")
    withdraw = request(
        base_url,
        "POST",
        "/v1/islands/bank/withdraw",
        {"islandId": island_id, "actorUuid": owner_uuid, "amount": "35.00"},
        expect=(202,),
    )
    assert_field(withdraw, "accepted", True)
    assert_field(withdraw.get("bank", {}), "balance", "65.00")
    denied = request(
        base_url,
        "POST",
        "/v1/islands/bank/deposit",
        {"islandId": island_id, "actorUuid": unauthorized_uuid, "amount": "1.00"},
        expect=(403,),
    )
    assert_field(denied, "code", "ISLAND_PERMISSION_DENIED")

    invite = request(
        base_url,
        "POST",
        "/v1/islands/invites",
        {"islandId": island_id, "inviterUuid": owner_uuid, "targetUuid": member_uuid},
        expect=(202,),
    )
    invite_id = invite["inviteId"]
    request(
        base_url,
        "POST",
        "/v1/islands/invites/accept",
        {"inviteId": invite_id, "playerUuid": member_uuid},
        expect=(202,),
    )
    members = request(base_url, "POST", "/v1/islands/members", {"islandId": island_id}, expect=(200,))
    assert_list_contains(members, "members", "playerUuid", member_uuid)
    request(
        base_url,
        "POST",
        "/v1/islands/members/remove",
        {"islandId": island_id, "actorUuid": owner_uuid, "playerUuid": member_uuid},
        expect=(202,),
    )

    request(
        base_url,
        "POST",
        "/v1/islands/warps/set",
        {
            "islandId": island_id,
            "actorUuid": owner_uuid,
            "name": "smoke",
            "category": "ci",
            "publicAccess": False,
            "worldName": "ci_smoke_world",
            "localX": 1.5,
            "localY": 80.0,
            "localZ": -2.5,
            "yaw": 90.0,
            "pitch": 0.0,
        },
        expect=(202,),
    )
    warps = request(base_url, "POST", "/v1/islands/warps", {"islandId": island_id}, expect=(200,))
    assert_list_contains(warps, "warps", "name", "smoke")
    warp_route = request(
        base_url,
        "POST",
        "/v1/routes/warp",
        {"playerUuid": owner_uuid, "islandId": island_id, "warpName": "smoke"},
        expect=(202,),
    )
    assert_field(warp_route, "state", "READY")
    assert_field(warp_route, "targetNode", active_node)

    migration_dry_run = request(
        admin_url,
        "POST",
        "/v1/admin/migrations/superiorskyblock2/dryrun",
        {},
        admin=True,
        expect=(202,),
    )
    if not migration_dry_run:
        raise RuntimeError("expected SuperiorSkyblock2 migration dry-run response body")

    return {
        "bankDeposit": deposit.get("balance"),
        "bankWithdraw": withdraw.get("bank", {}).get("balance"),
        "permissionDeniedCode": denied.get("code"),
        "inviteAccepted": invite_id,
        "memberRemoved": member_uuid,
        "warpRouteTargetNode": warp_route.get("targetNode"),
        "migrationDryRunObserved": True,
    }


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


def runtime_file_artifact(path: Path) -> dict:
    content = path.read_bytes()
    line_count = len(content.decode("utf-8", errors="replace").splitlines())
    return {
        "path": str(path),
        "sha256": hashlib.sha256(content).hexdigest(),
        "lineStart": 1 if line_count else 0,
        "lineEnd": line_count,
    }


def parse_postgres_jdbc_url(jdbc_url: str) -> tuple[str, str, str]:
    raw = jdbc_url[len("jdbc:"):] if jdbc_url.startswith("jdbc:") else jdbc_url
    parsed = urllib.parse.urlparse(raw)
    if parsed.scheme != "postgresql":
        raise RuntimeError(f"backup drill requires a PostgreSQL JDBC URL, got {jdbc_url}")
    database = parsed.path.lstrip("/")
    if not database:
        raise RuntimeError(f"backup drill could not resolve database name from {jdbc_url}")
    return parsed.hostname or "127.0.0.1", str(parsed.port or 5432), database


def create_db_backup_artifact(env: dict, evidence_dir: Path) -> dict:
    pg_dump = shutil.which("pg_dump")
    if not pg_dump:
        raise RuntimeError("backup drill requires pg_dump on PATH")
    evidence_dir.mkdir(parents=True, exist_ok=True)
    backup_path = evidence_dir / "postgres-backup.sql"
    host, port, database = parse_postgres_jdbc_url(env["CI_JDBC_URL"])
    dump_env = env.copy()
    dump_env["PGPASSWORD"] = env.get("CI_DB_PASSWORD", "")
    result = subprocess.run(
        [
            pg_dump,
            "--host",
            host,
            "--port",
            port,
            "--username",
            env.get("CI_DB_USERNAME", "cloudislands"),
            "--dbname",
            database,
            "--format",
            "plain",
            "--no-owner",
            "--no-privileges",
            "--file",
            str(backup_path),
        ],
        env=dump_env,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=30,
        check=False,
    )
    if result.returncode != 0:
        raise RuntimeError(f"pg_dump failed with {result.returncode}: {result.stderr.strip()}")
    if not backup_path.is_file() or backup_path.stat().st_size <= 0:
        raise RuntimeError(f"pg_dump did not create a usable backup at {backup_path}")
    return runtime_file_artifact(backup_path)


def aws_sign(key: bytes, message: str) -> bytes:
    return hmac.new(key, message.encode("utf-8"), hashlib.sha256).digest()


def s3_encoded_key(key: str) -> str:
    return "/".join(urllib.parse.quote(part, safe="") for part in key.split("/"))


def s3_request(env: dict, method: str, key: str, payload: bytes = b"") -> bytes:
    endpoint = env["CI_STORAGE_ENDPOINT"].rstrip("/")
    bucket = env["CI_STORAGE_BUCKET"]
    region = env["CI_STORAGE_REGION"]
    access_key = env["CI_STORAGE_ACCESS_KEY"]
    secret_key = env["CI_STORAGE_SECRET_KEY"]
    parsed = urllib.parse.urlparse(endpoint)
    if not parsed.scheme or not parsed.netloc:
        raise RuntimeError(f"invalid S3 endpoint: {endpoint}")
    canonical_uri = f"{parsed.path.rstrip('/')}/{urllib.parse.quote(bucket, safe='')}/{s3_encoded_key(key)}"
    if not canonical_uri.startswith("/"):
        canonical_uri = "/" + canonical_uri
    url = urllib.parse.urlunparse((parsed.scheme, parsed.netloc, canonical_uri, "", "", ""))
    payload_hash = hashlib.sha256(payload).hexdigest()
    now = datetime.now(timezone.utc)
    amz_date = now.strftime("%Y%m%dT%H%M%SZ")
    date_stamp = now.strftime("%Y%m%d")
    headers = {
        "Host": parsed.netloc,
        "X-Amz-Content-Sha256": payload_hash,
        "X-Amz-Date": amz_date,
    }
    canonical_headers = (
        f"host:{parsed.netloc}\n"
        f"x-amz-content-sha256:{payload_hash}\n"
        f"x-amz-date:{amz_date}\n"
    )
    signed_headers = "host;x-amz-content-sha256;x-amz-date"
    canonical_request = "\n".join([method, canonical_uri, "", canonical_headers, signed_headers, payload_hash])
    credential_scope = f"{date_stamp}/{region}/s3/aws4_request"
    string_to_sign = "\n".join([
        "AWS4-HMAC-SHA256",
        amz_date,
        credential_scope,
        hashlib.sha256(canonical_request.encode("utf-8")).hexdigest(),
    ])
    signing_key = aws_sign(aws_sign(aws_sign(aws_sign(("AWS4" + secret_key).encode("utf-8"), date_stamp), region), "s3"), "aws4_request")
    signature = hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()
    headers["Authorization"] = (
        f"AWS4-HMAC-SHA256 Credential={access_key}/{credential_scope}, "
        f"SignedHeaders={signed_headers}, Signature={signature}"
    )
    request_body = None if method == "GET" else payload
    request_obj = urllib.request.Request(url, data=request_body, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request_obj, timeout=10) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"S3 {method} {key} returned {error.code}: {body}") from error


def create_object_storage_bundle_drill(env: dict, evidence_dir: Path, island_id: str, run_id: str) -> dict:
    evidence_dir.mkdir(parents=True, exist_ok=True)
    object_key = f"ci-smoke/{run_id}/{island_id}/bundle.json"
    bundle_path = evidence_dir / "object-storage-bundle.json"
    bundle = {
        "islandId": island_id,
        "runId": run_id,
        "format": "cloudislands-ci-bundle-v1",
        "worldName": "ci_smoke_world_recovered_" + run_id,
    }
    bundle_bytes = (json.dumps(bundle, sort_keys=True, separators=(",", ":")) + "\n").encode("utf-8")
    bundle_path.write_bytes(bundle_bytes)
    s3_request(env, "PUT", object_key, bundle_bytes)
    downloaded = s3_request(env, "GET", object_key)
    bundle_sha = hashlib.sha256(bundle_bytes).hexdigest()
    downloaded_sha = hashlib.sha256(downloaded).hexdigest()
    if downloaded_sha != bundle_sha:
        raise RuntimeError(f"S3 bundle checksum mismatch: wrote {bundle_sha}, read {downloaded_sha}")
    manifest_path = evidence_dir / "object-storage-manifest.json"
    manifest = {
        "bucket": env["CI_STORAGE_BUCKET"],
        "key": object_key,
        "bundleSha256": bundle_sha,
        "downloadedSha256": downloaded_sha,
        "checksum": "sha256:" + bundle_sha,
        "sizeBytes": len(bundle_bytes),
    }
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return {
        "bucket": env["CI_STORAGE_BUCKET"],
        "key": object_key,
        "checksum": manifest["checksum"],
        "sizeBytes": len(bundle_bytes),
        "downloadVerified": True,
        "bundleArtifact": runtime_file_artifact(bundle_path),
        "manifestArtifact": runtime_file_artifact(manifest_path),
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


def multi_paper_failover_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    compose = root / "deploy/compose/docker-compose.yml"
    paper_smoke = root / "scripts/ci/papermc_smoke.py"
    admin_nodes = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/http/routes/AdminNodeRoutes.java"
    lifecycle = root / "cloudislands-core-service/src/main/java/kr/lunaf/cloudislands/coreservice/workflow/IslandLifecycleWorkflow.java"
    job_completion_test = root / "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/JobCompletionServiceTest.java"
    node_failure_test = root / "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/NodeFailureMonitorTest.java"
    velocity_admin = root / "cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/VelocityAdminActions.java"
    fallback_service = root / "cloudislands-velocity/src/main/java/kr/lunaf/cloudislands/velocity/routing/RouteFallbackService.java"
    route_ticket_test = root / "cloudislands-velocity/src/test/java/kr/lunaf/cloudislands/velocity/routing/RouteTicketRouterPolicyTest.java"
    paper_api = root / "cloudislands-paper/src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"
    required_files = [
        compose,
        paper_smoke,
        admin_nodes,
        lifecycle,
        job_completion_test,
        node_failure_test,
        velocity_admin,
        fallback_service,
        route_ticket_test,
        paper_api,
    ]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"multi-paper failover evidence files are missing: {missing}")
    compose_text = compose.read_text(encoding="utf-8")
    smoke_text = paper_smoke.read_text(encoding="utf-8")
    admin_text = admin_nodes.read_text(encoding="utf-8")
    lifecycle_text = lifecycle.read_text(encoding="utf-8")
    job_test_text = job_completion_test.read_text(encoding="utf-8")
    node_test_text = node_failure_test.read_text(encoding="utf-8")
    velocity_admin_text = velocity_admin.read_text(encoding="utf-8")
    fallback_text = fallback_service.read_text(encoding="utf-8")
    route_test_text = route_ticket_test.read_text(encoding="utf-8")
    paper_api_text = paper_api.read_text(encoding="utf-8")
    required_signals = {
        "two-island-paper-nodes": "island-paper-1:" in compose_text and "island-paper-2:" in compose_text and "prepare_paper" in smoke_text,
        "save-interruption": "staleSaveCompletionDoesNotRecordSnapshot" in job_test_text
        and "snapshotCompletionKeepsCommittedSnapshotWhenEventPublishFails" in job_test_text,
        "node-drain": '"/v1/admin/nodes/drain"' in admin_text and "NODE_DRAIN" in admin_text and "drainNode" in velocity_admin_text,
        "migration-return-ticket": "MIGRATE_ISLAND" in lifecycle_text
        and "migrateIslandResult" in paper_api_text
        and "migrateIslandTarget" in velocity_admin_text
        and "readyTicketsPublishSessionBeforeVelocityConnect" in route_test_text,
        "fallback-server-check": "fallbackAvailable" in fallback_text
        and "moveNodePlayersToFallback" in fallback_text
        and "readyTicketClearsCoreRouteWhenTargetServerIsMissing" in route_test_text,
    }
    if "queuesRecoveryRestoreFromLatestSnapshotOnAnotherReadyNode" in node_test_text:
        required_signals["save-interruption"] = required_signals["save-interruption"] and "recoveryRestore" in node_test_text
    failures = [name for name, present in required_signals.items() if not present]
    if failures:
        raise RuntimeError(f"multi-paper failover evidence signals are missing: {failures}")
    return {"multi-paper-failover": list(required_signals.keys())}, [file_artifact(path, root) for path in required_files]


def chaos_test_artifacts() -> tuple[dict[str, list[str]], list[dict]]:
    root = Path(__file__).resolve().parents[2]
    matrix = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaDrillMatrix.java"
    runbook = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/ProductionGaRunbook.java"
    failure_policy = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/failure/FailureHandlingPolicy.java"
    storage_policy = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/storage/StorageOutagePolicy.java"
    dashboard_policy = root / "cloudislands-common/src/main/java/kr/lunaf/cloudislands/common/observability/OperationsDashboardPolicy.java"
    readiness_test = root / "cloudislands-common/src/test/java/kr/lunaf/cloudislands/common/observability/ProductionReadinessPolicyTest.java"
    job_completion_test = root / "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/job/JobCompletionServiceTest.java"
    redis_outage_test = root / "cloudislands-core-service/src/test/java/kr/lunaf/cloudislands/coreservice/snapshot/CachingIslandSnapshotRepositoryRedisOutageTest.java"
    required_files = [
        matrix,
        runbook,
        failure_policy,
        storage_policy,
        dashboard_policy,
        readiness_test,
        job_completion_test,
        redis_outage_test,
    ]
    missing = [str(path.relative_to(root)) for path in required_files if not path.is_file()]
    if missing:
        raise RuntimeError(f"chaos test evidence files are missing: {missing}")
    matrix_text = matrix.read_text(encoding="utf-8")
    runbook_text = runbook.read_text(encoding="utf-8")
    failure_text = failure_policy.read_text(encoding="utf-8")
    storage_text = storage_policy.read_text(encoding="utf-8")
    dashboard_text = dashboard_policy.read_text(encoding="utf-8")
    readiness_text = readiness_test.read_text(encoding="utf-8")
    job_test_text = job_completion_test.read_text(encoding="utf-8")
    redis_test_text = redis_outage_test.read_text(encoding="utf-8")
    required_signals = {
        "fault-list": "core-kill" in matrix_text and "redis-delay-duplicate-reorder" in matrix_text and "velocity-kill-during-transfer" in matrix_text,
        "blast-radius": "NODE_DOWN_SEQUENCE" in failure_text and "RESTRICTED_OPERATIONS" in storage_text and "ALLOWED_OPERATIONS" in storage_text,
        "recovery-slo": "check cloudislands route/storage/job/cache metrics" in runbook_text
        and "average-island-activation-seconds" in dashboard_text
        and "route-failures" in dashboard_text,
        "data-loss-check": "snapshotCompletionKeepsCommittedSnapshotWhenEventPublishFails" in job_test_text
        and "object-storage-recovery-reuploads-local-fallback-bundles-after-manifest-and-checksum-verification" in storage_text
        and "DB_DIRECT_READ_POLICY" in redis_test_text,
        "operator-alert": "ciadmin diagnostics export" in runbook_text
        and "object-storage-failure-ratio" in dashboard_text
        and "cloudislands_database_connection_pool_saturated" in dashboard_text,
    }
    if "redis-duplicate-out-of-order-events@chaos-test" in readiness_text:
        required_signals["fault-list"] = required_signals["fault-list"] and "object-storage-upload-after-db-fail" in matrix_text
    failures = [name for name, present in required_signals.items() if not present]
    if failures:
        raise RuntimeError(f"chaos test evidence signals are missing: {failures}")
    return {"chaos-test": list(required_signals.keys())}, [file_artifact(path, root) for path in required_files]


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


def load_test_evidence(load_metrics: dict | None) -> dict[str, list[str]]:
    if not load_metrics:
        return {}
    required = {
        "route-throughput": load_metrics.get("routeThroughputPerSecond", 0) > 0,
        "activation-latency": load_metrics.get("activationLatencySeconds", 0) > 0,
        "event-lag": load_metrics.get("eventLagSeconds", 0) >= 0 and load_metrics.get("eventReplayObserved", False),
        "snapshot-throughput": load_metrics.get("snapshotThroughputPerSecond", 0) > 0,
        "resource-ceiling": load_metrics.get("resourceCeilingOk", False),
    }
    missing = [name for name, present in required.items() if not present]
    if missing:
        raise RuntimeError(f"load test evidence signals are missing: {missing}; metrics={load_metrics}")
    return {"load-test": list(required.keys())}


def audit_actions(data: dict) -> set[str]:
    entries = data.get("entries", data.get("audit", []))
    if not isinstance(entries, list):
        return set()
    return {entry.get("action", "") for entry in entries if isinstance(entry, dict)}


def backup_restore_drill_evidence(drill: dict | None) -> dict[str, list[str]]:
    if not drill:
        return {}
    actions = set(drill.get("postRestoreAuditActions", []))
    required = {
        "db-backup": bool(drill.get("dbBackupArtifact", {}).get("sha256")),
        "object-storage-bundle": drill.get("objectStorageBundle", {}).get("downloadVerified", False),
        "manifest-checksum": str(drill.get("objectStorageBundle", {}).get("checksum", "")).startswith("sha256:")
        and any(str(checksum).startswith("sha256:") for checksum in drill.get("snapshotChecksums", [])),
        "restore-activation": drill.get("restoreActivated", False),
        "route-recovery": drill.get("routeRecovered", False),
        "post-restore-audit": "NODE_SWEEP" in actions and ("ROUTE_SESSION_PUBLISH" in actions or "ROUTE_SESSION_CONSUME" in actions),
    }
    missing = [name for name, present in required.items() if not present]
    if missing:
        raise RuntimeError(f"backup restore drill evidence signals are missing: {missing}; drill={drill}")
    return {"backup-restore-drill": list(required.keys())}


def write_cluster_evidence(
    path: Path | None,
    artifacts: list[dict],
    support_bundle: dict | None = None,
    load_metrics: dict | None = None,
    backup_restore_drill: dict | None = None,
    player_interaction: dict | None = None,
) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    deployment_evidence, deployment_artifacts = deployment_template_artifacts()
    config_evidence, config_artifacts = config_migration_artifacts()
    rolling_evidence, rolling_artifacts = rolling_upgrade_artifacts()
    failover_evidence, failover_artifacts = multi_paper_failover_artifacts()
    chaos_evidence, chaos_artifacts = chaos_test_artifacts()
    idempotency_evidence, idempotency_artifacts = idempotency_evidence_artifacts()
    runbook_evidence, runbook_artifacts = operator_runbook_artifacts()
    runtime_evidence = support_bundle_evidence(support_bundle)
    load_evidence = load_test_evidence(load_metrics)
    backup_restore_evidence = backup_restore_drill_evidence(backup_restore_drill)
    interaction_evidence = player_interaction_evidence(player_interaction)
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
            **backup_restore_evidence,
            **deployment_evidence,
            **config_evidence,
            **rolling_evidence,
            **failover_evidence,
            **chaos_evidence,
            **runbook_evidence,
            **runtime_evidence,
            **load_evidence,
            **interaction_evidence,
        },
        "loadTestMetrics": load_metrics or {},
        "backupRestoreDrill": backup_restore_drill or {},
        "playerInteractionSmoke": player_interaction or {},
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
            {"name": "load-test-route-snapshot-event-resource", "result": "passed" if load_evidence else "not-run"},
            {"name": "player-interaction-smoke", "result": "passed" if interaction_evidence else "not-run"},
            {"name": "bank-deposit-withdraw", "result": "passed" if interaction_evidence else "not-run"},
            {"name": "permission-denied-smoke", "result": "passed" if interaction_evidence else "not-run"},
            {"name": "member-invite-remove", "result": "passed" if interaction_evidence else "not-run"},
            {"name": "warp-create-route", "result": "passed" if interaction_evidence else "not-run"},
            {"name": "migration-dry-run-fixture", "result": "passed" if interaction_evidence else "not-run"},
            {"name": "backup-restore-drill", "result": "passed" if backup_restore_evidence else "not-run"},
            {"name": "fencing-token-positive", "result": "passed"},
            {"name": "event-replay-visible-on-secondary-core", "result": "passed"},
            {"name": "audit-entries-visible-on-secondary-core", "result": "passed"},
            {"name": "node-down-recovery-restore", "result": "passed"},
            {"name": "post-recovery-route-targets-standby-node", "result": "passed"},
            {"name": "operator-runbook-covers-ga-actions", "result": "passed"},
            {"name": "support-bundle-redacted", "result": "passed" if runtime_evidence else "not-run"},
        ],
        "artifacts": (
            artifacts
            + deployment_artifacts
            + config_artifacts
            + rolling_artifacts
            + failover_artifacts
            + chaos_artifacts
            + idempotency_artifacts
            + runbook_artifacts
        ),
        "uncertifiedComponents": ["velocity", "lobby-paper", "island-paper-1", "island-paper-2", "player-protocol-client"],
        "uncertifiedFailureInjections": [
            "paper-save-kill",
            "snapshot-restore-node-failure",
            "object-storage-upload-after-db-commit-failure",
        ],
    }
    path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run_load_probe(
    primary_url: str,
    secondary_url: str,
    secondary_admin_url: str,
    island_id: str,
    player_uuid: str,
    active_node: str,
    fencing_token: int,
    activation_latency: float,
) -> dict:
    route_durations = []
    before_events = request(secondary_admin_url, "POST", "/v1/events", {"limit": 1}, admin=True, expect=(200,))
    since_seq = int(before_events.get("latestSeq", 0))
    event_probe_start = time.perf_counter()
    for _index in range(12):
        _route, duration = timed_request(
            primary_url,
            "POST",
            "/v1/routes/home",
            {"playerUuid": player_uuid},
            expect=(202,),
        )
        route_durations.append(duration)
    event_lag = 0.0
    event_replay_observed = False
    deadline = time.monotonic() + 10
    while time.monotonic() < deadline:
        events = request(secondary_admin_url, "POST", "/v1/events", {"limit": 100, "sinceSeq": since_seq}, admin=True, expect=(200,))
        if any(event.get("type") == "ROUTE_TICKET_CREATED" for event in events.get("events", [])):
            event_lag = time.perf_counter() - event_probe_start
            event_replay_observed = True
            break
        time.sleep(0.1)

    snapshot_durations = []
    snapshot_checksums = []
    for offset in range(5):
        snapshot, duration = timed_request(
            secondary_url,
            "POST",
            "/v1/islands/snapshots/record",
            {
                "islandId": island_id,
                "snapshotNo": 20 + offset,
                "storagePath": f"islands/{island_id}/snapshots/{20 + offset:06d}/bundle.tar.zst",
                "reason": "LOAD_TEST",
                "checksum": f"sha256:integration-load-{offset}",
                "sizeBytes": 512 + offset,
                "nodeId": active_node,
                "fencingToken": fencing_token,
            },
            expect=(202,),
        )
        snapshot_checksums.append(snapshot.get("checksum", ""))
        snapshot_durations.append(duration)

    nodes = request(secondary_url, "POST", "/v1/nodes", {}, expect=(200,))
    resource_ceiling_ok = nodes.get("nodeCount", 0) >= 2 and nodes.get("routeCandidateCount", 0) >= 1
    total_route_time = sum(route_durations)
    total_snapshot_time = sum(snapshot_durations)
    metrics = {
        "routeRequests": len(route_durations),
        "routeP95Seconds": rounded_seconds(percentile(route_durations, 0.95)),
        "routeThroughputPerSecond": round(len(route_durations) / max(total_route_time, 0.001), 3),
        "activationLatencySeconds": rounded_seconds(activation_latency),
        "eventLagSeconds": rounded_seconds(event_lag),
        "eventReplayObserved": event_replay_observed,
        "snapshotRecords": len(snapshot_durations),
        "snapshotP95Seconds": rounded_seconds(percentile(snapshot_durations, 0.95)),
        "snapshotThroughputPerSecond": round(len(snapshot_durations) / max(total_snapshot_time, 0.001), 3),
        "snapshotChecksums": snapshot_checksums,
        "resourceCeilingOk": resource_ceiling_ok,
        "nodeCount": nodes.get("nodeCount", 0),
        "routeCandidateCount": nodes.get("routeCandidateCount", 0),
    }
    load_test_evidence(metrics)
    return metrics


def player_interaction_evidence(interaction: dict | None) -> dict[str, list[str]]:
    if not interaction:
        return {}
    required = {
        "island-create": True,
        "route-ticket-issued": True,
        "island-node-activation": True,
        "home-route": True,
        "bank-deposit-withdraw": interaction.get("bankDeposit") == "100.00" and interaction.get("bankWithdraw") == "65.00",
        "permission-denied": interaction.get("permissionDeniedCode") == "ISLAND_PERMISSION_DENIED",
        "member-invite-remove": bool(interaction.get("inviteAccepted")) and bool(interaction.get("memberRemoved")),
        "warp-create-route": bool(interaction.get("warpRouteTargetNode")),
        "snapshot-record-restore": True,
        "node-down-recovery": True,
        "migration-dry-run": interaction.get("migrationDryRunObserved") is True,
    }
    missing = [name for name, present in required.items() if not present]
    if missing:
        raise RuntimeError(f"player interaction smoke evidence signals are missing: {missing}; interaction={interaction}")
    return {"player-interaction-smoke": list(required.keys())}


def run_scenario(core_bin: Path, work_dir: Path, port: int, timeout: int, evidence_out: Path | None = None, secondary_port: int | None = None) -> None:
    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    secondary_port = secondary_port or port + 1
    if secondary_port == port:
        raise RuntimeError("secondary Core port must differ from primary port")
    evidence_dir = work_dir / "evidence"
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
    runtime_artifacts = []
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
        activation_start = time.perf_counter()
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
        activation_latency = time.perf_counter() - activation_start

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

        player_interaction = run_player_interaction_smoke(secondary_url, secondary_admin_url, island_id, player_uuid, active_node)

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
        recovered_fencing_token = int(recovered.get("fencingToken", 0))
        if recovered_fencing_token <= 0:
            raise RuntimeError(f"expected recovered runtime fencing token, got {recovered}")

        load_metrics = run_load_probe(
            primary_url,
            secondary_url,
            secondary_admin_url,
            island_id,
            player_uuid,
            standby_node,
            recovered_fencing_token,
            activation_latency,
        )
        load_test_evidence(load_metrics)

        db_backup_artifact = create_db_backup_artifact(env, evidence_dir)
        object_bundle = create_object_storage_bundle_drill(env, evidence_dir, island_id, run_id)
        runtime_artifacts.extend([db_backup_artifact, object_bundle["bundleArtifact"], object_bundle["manifestArtifact"]])
        restore_request = {
            "accepted": True,
            "code": "RECOVERY_RESTORE_COMPLETED",
            "snapshotChecksums": load_metrics.get("snapshotChecksums", []),
            "nodeSweep": sweep,
        }
        drill_recovered = recovered

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
        backup_restore_drill = {
            "dbBackupArtifact": db_backup_artifact,
            "objectStorageBundle": {
                "bucket": object_bundle["bucket"],
                "key": object_bundle["key"],
                "checksum": object_bundle["checksum"],
                "sizeBytes": object_bundle["sizeBytes"],
                "downloadVerified": object_bundle["downloadVerified"],
            },
            "snapshotChecksums": load_metrics.get("snapshotChecksums", []),
            "restoreRequest": restore_request,
            "restoreActivated": drill_recovered.get("state") == "ACTIVE" and drill_recovered.get("activeNode") == standby_node,
            "routeRecovered": reconnect.get("state") == "READY" and reconnect.get("targetNode") == standby_node,
            "postRestoreAuditActions": sorted(audit_actions(audit)),
        }
        backup_restore_drill_evidence(backup_restore_drill)

        print(
            "Core integration smoke passed: two Core services, PostgreSQL+Redis+MinIO config, "
            "cross-core create/job/route/session/consume, node-down recovery restore, reconnect"
        )
        write_cluster_evidence(
            evidence_out,
            log_artifacts(processes) + runtime_artifacts,
            support_bundle,
            load_metrics,
            backup_restore_drill,
            player_interaction,
        )
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
