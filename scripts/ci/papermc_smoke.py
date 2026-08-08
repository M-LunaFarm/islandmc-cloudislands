#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import selectors
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


USER_AGENT = "cloudislands-ci-smoke/1.0"


def channel_download(project: str, version: str, channel: str) -> tuple[str, str, int]:
    requested_channel = channel.strip().upper()
    if requested_channel not in {"STABLE", "BETA", "ALPHA"}:
        raise RuntimeError(f"unsupported PaperMC build channel: {channel}")
    url = f"https://fill.papermc.io/v3/projects/{project}/versions/{version}/builds"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        builds = json.load(response)
    for build in builds:
        if build.get("channel") == requested_channel:
            downloads = build.get("downloads", {})
            server = downloads.get("server:default", {})
            download_url = server.get("url")
            checksum = server.get("checksums", {}).get("sha256")
            size = server.get("size")
            if download_url and checksum and isinstance(size, int) and size > 0:
                return download_url, checksum.lower(), size
    raise RuntimeError(f"no {requested_channel.lower()} {project} build found for {version}")


def stable_download(project: str, version: str) -> tuple[str, str, int]:
    return channel_download(project, version, "STABLE")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(url: str, target: Path, expected_checksum: str, expected_size: int) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        if target.stat().st_size == expected_size and sha256(target) == expected_checksum:
            return
        target.unlink()
    tmp = target.with_suffix(target.suffix + ".tmp")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, tmp.open("wb") as out:
        shutil.copyfileobj(response, out)
    if tmp.stat().st_size != expected_size or sha256(tmp) != expected_checksum:
        tmp.unlink(missing_ok=True)
        raise RuntimeError(f"download checksum mismatch: {target.name}")
    tmp.replace(target)


def prepare_paper(work_dir: Path, plugin: Path, java_command: str, bootstrap_failure: bool = False) -> list[str]:
    cloudislands_dir = work_dir / "plugins" / "CloudIslands"
    config_v2_dir = cloudislands_dir / "config-v2"
    cloudislands_dir.mkdir(parents=True, exist_ok=True)
    config_v2_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(plugin, work_dir / "plugins" / plugin.name)
    (config_v2_dir / "config.yml").write_text(
        "config-version: 2\nconfiguration-mode: ADVANCED\nprofile: smoke\nlanguage: ko_kr\nstrict-validation: true\n",
        encoding="utf-8",
    )
    (work_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work_dir / "server.properties").write_text(
        "\n".join(
            [
                "online-mode=false",
                "enforce-secure-profile=false",
                "server-port=25580",
                "enable-query=false",
                "enable-rcon=false",
                "allow-nether=false",
                "generate-structures=false",
                "level-type=minecraft:flat",
                "spawn-protection=0",
                "view-distance=2",
                "simulation-distance=2",
                "motd=CloudIslands smoke",
                "",
            ]
        ),
        encoding="utf-8",
    )
    (work_dir / "bukkit.yml").write_text(
        "settings:\n  allow-end: false\n",
        encoding="utf-8",
    )
    (config_v2_dir / "runtime.yml").write_text(
        "\n".join(
            [
                "node:",
                f"  role: {'ISLAND_NODE' if bootstrap_failure else 'LOBBY'}",
                f"  id: {'island-1' if bootstrap_failure else 'smoke-lobby'}",
                f"  pool: {'island' if bootstrap_failure else 'lobby'}",
                f"  velocity-server-name: {'Island-1' if bootstrap_failure else 'Lobby'}",
                f"  reject-default-identity: {'true' if bootstrap_failure else 'false'}",
                "  supported-templates:",
                "    - \"*\"",
                "capacity:",
                "  max-active-islands: 1",
                "  soft-player-limit: 10",
                "  hard-player-limit: 20",
                "  max-activation-queue: 1",
                "heartbeat:",
                "  interval: 5s",
                "health:",
                "  enabled: false",
                "  bind-host: 127.0.0.1",
                "  port: 8789",
                "",
            ]
        ),
        encoding="utf-8",
    )
    (config_v2_dir / "integrations.yml").write_text(
        "\n".join(
            [
                "core-api:",
                "  base-url: http://127.0.0.1:9",
                "  timeout:",
                "    request: 100ms",
                "redis:",
                "  uri: \"\"",
                "storage:",
                "  type: LOCAL_FILESYSTEM",
                "",
            ]
        ),
        encoding="utf-8",
    )
    (config_v2_dir / "security.yml").write_text(
        "\n".join(
            [
                "core-api:",
                "  auth-token: ${env:CI_CORE_TOKEN}",
                "  admin-token: ${env:CI_ADMIN_TOKEN}",
                "forwarding:",
                "  required: false",
                "  secret: ${env:VELOCITY_FORWARDING_SECRET}",
                "route-session:",
                "  enforce: false",
                "  required: false",
                "trusted-proxies: []",
                "proxy-source-allowlist:",
                "  required: false",
                "storage:",
                "  bearer-token: ${env:S3_BEARER_TOKEN}",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return [java_command, "-Xms256m", "-Xmx768m", "-jar", "server.jar", "--nogui"]


def prepare_velocity(work_dir: Path, plugin: Path, java_command: str) -> list[str]:
    config_v2_dir = work_dir / "plugins" / "cloudislands" / "config-v2"
    config_v2_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(plugin, work_dir / "plugins" / plugin.name)
    (work_dir / "velocity.toml").write_text(
        "\n".join(
            [
                'config-version = "2.7"',
                'bind = "127.0.0.1:25581"',
                'motd = "CloudIslands smoke"',
                "show-max-players = 1",
                "online-mode = false",
                "force-key-authentication = false",
                'player-info-forwarding-mode = "none"',
                'forwarding-secret-file = "forwarding.secret"',
                "[servers]",
                'lobby = "127.0.0.1:25582"',
                'factions = "127.0.0.1:25583"',
                'minigames = "127.0.0.1:25584"',
                'try = ["lobby"]',
                "",
            ]
        ),
        encoding="utf-8",
    )
    (config_v2_dir / "core-api.yml").write_text(
        "\n".join(
            [
                "enabled: true",
                "base-url: http://127.0.0.1:9",
                "timeout:",
                "  connect: 100ms",
                "  request: 100ms",
                "",
            ]
        ),
        encoding="utf-8",
    )
    (config_v2_dir / "config.yml").write_text(
        "config-version: 2\nprofile: smoke\nlanguage: ko_kr\ndebug: true\nstrict-validation: true\n",
        encoding="utf-8",
    )
    (config_v2_dir / "routing.yml").write_text(
        "default-lobby: lobby\nisland-pool: island\nfailure:\n  fallback-server: lobby\n  hide-backend-node-names: true\n",
        encoding="utf-8",
    )
    (config_v2_dir / "security.yml").write_text(
        "core-api:\n  auth-token: ${env:CI_CORE_TOKEN}\n  admin-token: ${env:CI_ADMIN_TOKEN}\nforwarding:\n  require-modern: false\n  secret: ${env:VELOCITY_FORWARDING_SECRET}\nplugin-message:\n  block-cloudislands-channel: true\n",
        encoding="utf-8",
    )
    (config_v2_dir / "health.yml").write_text(
        "enabled: false\nbind-host: 127.0.0.1\nport: 18788\n",
        encoding="utf-8",
    )
    return [java_command, "-Xms256m", "-Xmx512m", "-jar", "server.jar"]


def wait_for_smoke(process: subprocess.Popen, log_path: Path, expected: list[str], ready: list[str], timeout: int) -> None:
    deadline = time.monotonic() + timeout
    seen_expected = set()
    seen_ready = False
    lines = []
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    with log_path.open("w", encoding="utf-8") as log:
        while time.monotonic() < deadline:
            remaining = max(0.0, deadline - time.monotonic())
            events = selector.select(timeout=min(0.25, remaining))
            if events:
                line = process.stdout.readline()
            else:
                line = ""
            if line:
                lines.append(line)
                log.write(line)
                log.flush()
                for marker in expected:
                    if marker in line:
                        seen_expected.add(marker)
                if any(marker in line for marker in ready):
                    seen_ready = True
                if any(marker in line for marker in ("[ERROR]", "[SEVERE]", "Exception in thread", "OutOfMemoryError")):
                    selector.close()
                    raise RuntimeError(f"fatal server log line during smoke: {line.strip()}")
                if seen_ready and len(seen_expected) == len(expected):
                    selector.close()
                    return
            if process.poll() is not None:
                break
    selector.close()
    tail = "".join(lines[-80:])
    raise RuntimeError(f"server smoke failed; expected={expected} seen={sorted(seen_expected)} ready={seen_ready}\n{tail}")


def wait_for_console_markers(process: subprocess.Popen, log_path: Path, expected: list[str], timeout: int, server_log_path: Path | None = None) -> None:
    deadline = time.monotonic() + timeout
    seen = set()
    lines = []
    selector = selectors.DefaultSelector()
    selector.register(process.stdout, selectors.EVENT_READ)
    with log_path.open("a", encoding="utf-8") as log:
        while time.monotonic() < deadline:
            if server_log_path is not None and server_log_path.exists():
                server_log = server_log_path.read_text(encoding="utf-8", errors="replace")
                for marker in expected:
                    if marker in server_log:
                        seen.add(marker)
                if len(seen) == len(expected):
                    selector.close()
                    return
            remaining = max(0.0, deadline - time.monotonic())
            events = selector.select(timeout=min(0.25, remaining))
            line = process.stdout.readline() if events else ""
            if line:
                lines.append(line)
                log.write(line)
                log.flush()
                for marker in expected:
                    if marker in line:
                        seen.add(marker)
                if any(marker in line for marker in ("[ERROR]", "[SEVERE]", "Exception in thread", "OutOfMemoryError")):
                    selector.close()
                    raise RuntimeError(f"fatal server log line during command smoke: {line.strip()}")
                if len(seen) == len(expected):
                    selector.close()
                    return
            if process.poll() is not None:
                break
    selector.close()
    tail = "".join(lines[-80:])
    raise RuntimeError(f"server command smoke failed; expected={expected} seen={sorted(seen)}\n{tail}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", choices=["paper", "velocity"], required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", choices=["STABLE", "BETA", "ALPHA"], default="STABLE", type=str.upper)
    parser.add_argument("--plugin", required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--cache-dir", required=True)
    parser.add_argument("--java-command", default="java")
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--paper-bootstrap-failure", action="store_true")
    args = parser.parse_args()

    plugin = Path(args.plugin).resolve()
    work_dir = Path(args.work_dir).resolve()
    cache_dir = Path(args.cache_dir).resolve()
    if not plugin.exists():
        raise RuntimeError(f"plugin jar does not exist: {plugin}")

    download_url, expected_checksum, expected_size = channel_download(args.project, args.version, args.channel)
    server_jar = cache_dir / Path(download_url).name
    download(download_url, server_jar, expected_checksum, expected_size)

    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    shutil.copy2(server_jar, work_dir / "server.jar")

    if args.project == "paper":
        command = prepare_paper(work_dir, plugin, args.java_command, args.paper_bootstrap_failure)
        expected = ["CloudIslands Paper entered bootstrap diagnostic mode"] if args.paper_bootstrap_failure else ["CloudIslands Paper agent enabled"]
        # Paper 1.21 logs the traditional final marker, while Paper 26.1+
        # reports world readiness as "Done preparing level".
        ready = ["Done (", "Done preparing level"]
        shutdown = "stop\n"
    else:
        if args.paper_bootstrap_failure:
            raise RuntimeError("--paper-bootstrap-failure is only valid for Paper smoke")
        command = prepare_velocity(work_dir, plugin, args.java_command)
        expected = ["CloudIslands Velocity config loaded", "health=127.0.0.1:18788", "CloudIslands Velocity router enabled"]
        ready = ["Listening on"]
        shutdown = "end\n"

    env = os.environ.copy()
    env.setdefault("CI_CORE_TOKEN", "smoke")
    env.setdefault("CI_ADMIN_TOKEN", "smoke")
    env.setdefault("VELOCITY_FORWARDING_SECRET", "smoke")
    env.setdefault("S3_BEARER_TOKEN", "smoke")
    process = subprocess.Popen(
        command,
        cwd=work_dir,
        env=env,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        bufsize=1,
    )
    log_path = work_dir / "server.log"
    try:
        wait_for_smoke(process, log_path, expected, ready, args.timeout)
        if args.project == "paper" and process.stdin:
            process.stdin.write("ciadmin status\n")
            process.stdin.flush()
            command_expected = [
                "CloudIslands bootstrap=FAILED attempt=1"
                if args.paper_bootstrap_failure
                else "CloudIslands agent role=LOBBY node=smoke-lobby"
            ]
            wait_for_console_markers(process, log_path, command_expected, 30, work_dir / "logs" / "latest.log")
            if not args.paper_bootstrap_failure:
                messages_config = work_dir / "plugins" / "CloudIslands" / "config-v2" / "ui" / "messages" / "ko_kr.yml"
                messages = messages_config.read_text(encoding="utf-8").replace(
                    'admin-command-config-reload-applied-prefix: "Paper 설정 즉시 적용 완료: "',
                    'admin-command-config-reload-applied-prefix: "Paper config reload applied: "',
                ).replace(
                    'admin-command-config-reload-restart-prefix: "Paper 설정을 적용하려면 재시작이 필요합니다: "',
                    'admin-command-config-reload-restart-prefix: "Paper config reload requires restart: "',
                )
                messages_config.write_text(messages + '\nadmin-command-list-title: "LIVE_RELOAD_MARKER "\n', encoding="utf-8")
                process.stdin.write("ciadmin config reload\n")
                process.stdin.flush()
                wait_for_console_markers(
                    process,
                    log_path,
                    ["Paper config reload applied: messages"],
                    30,
                    work_dir / "logs" / "latest.log",
                )
                process.stdin.write("ciadmin help\n")
                process.stdin.flush()
                wait_for_console_markers(
                    process,
                    log_path,
                    ["LIVE_RELOAD_MARKER 1/"],
                    30,
                    work_dir / "logs" / "latest.log",
                )
                runtime_config = work_dir / "plugins" / "CloudIslands" / "config-v2" / "runtime.yml"
                restart_required = runtime_config.read_text(encoding="utf-8").replace("pool: lobby", "pool: changed-pool")
                runtime_config.write_text(restart_required, encoding="utf-8")
                process.stdin.write("ciadmin config reload\n")
                process.stdin.flush()
                wait_for_console_markers(
                    process,
                    log_path,
                    ["Paper config reload requires restart: node"],
                    30,
                    work_dir / "logs" / "latest.log",
                )
            else:
                runtime_config = work_dir / "plugins" / "CloudIslands" / "config-v2" / "runtime.yml"
                corrected = runtime_config.read_text(encoding="utf-8").replace("id: island-1", "id: smoke-island").replace(
                    "velocity-server-name: Island-1", "velocity-server-name: Smoke-Island"
                )
                runtime_config.write_text(corrected, encoding="utf-8")
                process.stdin.write("ciadmin retry\n")
                process.stdin.flush()
                wait_for_console_markers(
                    process,
                    log_path,
                    ["CloudIslands Paper agent enabled as ISLAND_NODE node smoke-island", "CloudIslands bootstrap=READY attempt=2"],
                    90,
                    work_dir / "logs" / "latest.log",
                )
        if process.stdin:
            process.stdin.write(shutdown)
            process.stdin.flush()
        try:
            process.wait(timeout=120)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=30)
    except Exception:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=30)
            except subprocess.TimeoutExpired:
                process.kill()
        raise
    if process.returncode not in (0, 143):
        raise RuntimeError(f"server exited with {process.returncode}; see {log_path}")
    print(f"{args.project} {args.version} boot smoke passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
