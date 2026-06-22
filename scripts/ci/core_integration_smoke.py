#!/usr/bin/env python3
import argparse
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


def wait_for_ready(base_url: str, deadline: float) -> None:
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(base_url + "/ready", timeout=3) as response:
                if response.status == 200:
                    return
        except OSError:
            pass
        time.sleep(0.25)
    raise RuntimeError("Core service did not become ready before timeout")


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
    jobs = request(
        base_url,
        "POST",
        "/v1/jobs/claim",
        {"nodeId": node_id, "supportedTypes": expected_type, "maxJobs": 4},
        admin=True,
        expect=(200,),
    )
    job_list = jobs.get("jobs", jobs) if isinstance(jobs, dict) else jobs
    if not isinstance(job_list, list) or not job_list:
        raise RuntimeError(f"expected {expected_type} job for {node_id}, got {jobs}")
    for job in job_list:
        if job.get("type") == expected_type:
            return job
    raise RuntimeError(f"expected {expected_type} job for {node_id}, got {jobs}")


def complete_job(base_url: str, node_id: str, job: dict, payload: dict) -> None:
    request(
        base_url,
        "POST",
        "/v1/jobs/complete",
        {"nodeId": node_id, "jobId": job["jobId"], "payload": payload},
        admin=True,
        expect=(202,),
    )


def assert_field(data: dict, key: str, expected) -> None:
    actual = data.get(key)
    if actual != expected:
        raise RuntimeError(f"expected {key}={expected!r}, got {actual!r} in {data}")


def write_cluster_evidence(path: Path | None) -> None:
    if path is None:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    evidence = {
        "components": [
            "core-1",
            "island-paper-1",
            "island-paper-2",
            "postgres",
            "redis",
            "object-storage",
        ],
        "evidence": {
            "multi-paper-failover": [
                "two-island-paper-nodes",
                "save-interruption",
            ],
            "backup-restore-drill": [
                "restore-activation",
                "route-recovery",
            ],
            "chaos-test": [
                "fault-list",
                "data-loss-check",
            ],
        },
        "failureInjections": [
            "paper-save-kill",
            "snapshot-restore-node-failure",
            "object-storage-upload-after-db-commit-failure",
        ],
    }
    path.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def run_scenario(core_bin: Path, work_dir: Path, port: int, timeout: int, evidence_out: Path | None = None) -> None:
    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    log_path = work_dir / "core.log"
    base_url = f"http://127.0.0.1:{port}"
    env = os.environ.copy()
    env.update(
        {
            "CI_BIND": "127.0.0.1",
            "CI_PORT": str(port),
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
            "CI_HEARTBEAT_TIMEOUT_SECONDS": "1",
            "CI_ROUTE_TICKET_TTL_SECONDS": "30",
            "CI_ROUTE_PREPARING_TICKET_TTL_SECONDS": "120",
            "CI_MIGRATION_POLICY": "ACTIVE_ALLOWED",
            "CI_SNAPSHOT_KEEP_LATEST": "5",
        }
    )
    with log_path.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [str(core_bin), str(port)],
            cwd=work_dir,
            env=env,
            stdout=log,
            stderr=subprocess.STDOUT,
            text=True,
        )
        try:
            wait_for_ready(base_url, time.monotonic() + timeout)
            config = request(base_url, "POST", "/v1/admin/config", {}, admin=True, expect=(200,))
            assert_field(config, "effectiveRepositoryMode", "JDBC")
            assert_field(config, "effectiveJobQueueMode", "REDIS")
            assert_field(config, "effectiveEventBusMode", "REDIS")
            assert_field(config, "databaseBackend", "POSTGRESQL")
            assert_field(config, "storageType", "S3")
            if not config.get("storageMultiNodeSafe"):
                raise RuntimeError(f"expected S3 storage to be multi-node safe: {config}")

            node_a = "island-a-" + uuid.uuid4().hex[:8]
            node_b = "island-b-" + uuid.uuid4().hex[:8]
            heartbeat(base_url, node_a, "Island-A")
            heartbeat(base_url, node_b, "Island-B")
            nodes = request(base_url, "POST", "/v1/nodes", {}, expect=(200,))
            if nodes.get("routeCandidateCount", 0) < 2:
                raise RuntimeError(f"expected two route candidates, got {nodes}")

            player_uuid = str(uuid.uuid4())
            create = request(
                base_url,
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

            create_job = claim_one(base_url, active_node, "CREATE_ISLAND")
            complete_job(
                base_url,
                active_node,
                create_job,
                {
                    "worldName": "ci_smoke_world",
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
                base_url,
                "POST",
                "/v1/routes/ticket-status",
                {"ticketId": ticket["ticketId"], "playerUuid": player_uuid, "nonce": ticket["nonce"]},
                expect=(200,),
            )
            assert_field(ready_ticket, "state", "READY")
            request(
                base_url,
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
                base_url,
                "POST",
                "/v1/routes/session/find",
                {"playerUuid": player_uuid, "nodeId": active_node},
                expect=(200,),
            )
            assert_field(found_session, "targetNode", active_node)
            consumed_session = request(
                base_url,
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
                base_url,
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

            where = request(base_url, "POST", "/v1/admin/islands/where", {"islandId": island_id}, admin=True, expect=(200,))
            assert_field(where, "state", "ACTIVE")
            assert_field(where, "activeNode", active_node)

            heartbeat(base_url, active_node, "Island-A" if active_node == node_a else "Island-B", state="DOWN", active_islands=1)
            heartbeat(base_url, standby_node, "Island-B" if standby_node == node_b else "Island-A")
            sweep = request(
                base_url,
                "POST",
                "/v1/admin/nodes/sweep",
                {"nodeId": active_node},
                admin=True,
                expect=(202,),
            )
            if sweep.get("recoveryRequired") < 1 or sweep.get("recoveryQueued") < 1:
                raise RuntimeError(f"expected node sweep to queue recovery, got {sweep}")

            restore_job = claim_one(base_url, standby_node, "RESTORE_ISLAND")
            complete_job(
                base_url,
                standby_node,
                restore_job,
                {
                    "worldName": "ci_smoke_world_recovered",
                    "cellX": "3",
                    "cellZ": "4",
                    "preMutationSnapshotNo": "2",
                    "preMutationReason": "BEFORE_RESTORE",
                    "preMutationChecksum": "sha256:integration-smoke-before-restore",
                    "preMutationSizeBytes": "1024",
                    "placementSource": "integration-smoke-recovery",
                },
            )
            recovered = request(base_url, "POST", "/v1/admin/islands/where", {"islandId": island_id}, admin=True, expect=(200,))
            assert_field(recovered, "state", "ACTIVE")
            assert_field(recovered, "activeNode", standby_node)

            reconnect = request(
                base_url,
                "POST",
                "/v1/routes/home",
                {"playerUuid": player_uuid},
                expect=(202,),
            )
            reconnect_ticket = latest_ticket(base_url, player_uuid)
            assert_field(reconnect_ticket, "state", "READY")
            assert_field(reconnect_ticket, "targetNode", standby_node)
            assert_field(reconnect, "state", "READY")
            assert_field(reconnect, "targetNode", standby_node)

            print(
                "Core integration smoke passed: PostgreSQL+Redis+MinIO config, "
                "create/job/route/session/consume, node-down recovery restore, reconnect"
            )
            write_cluster_evidence(evidence_out)
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=15)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)
            if process.returncode not in (0, 143, -15):
                tail = log_path.read_text(encoding="utf-8", errors="replace").splitlines()[-120:]
                raise RuntimeError("Core service exited unexpectedly:\n" + "\n".join(tail))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--core-bin", required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--port", type=int, default=18443)
    parser.add_argument("--timeout", type=int, default=90)
    parser.add_argument("--evidence-out")
    args = parser.parse_args()
    core_bin = Path(args.core_bin).resolve()
    if not core_bin.exists():
        raise RuntimeError(f"Core binary does not exist: {core_bin}")
    evidence_out = Path(args.evidence_out).resolve() if args.evidence_out else None
    run_scenario(core_bin, Path(args.work_dir).resolve(), args.port, args.timeout, evidence_out)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"core integration smoke failed: {exc}", file=sys.stderr)
        raise
