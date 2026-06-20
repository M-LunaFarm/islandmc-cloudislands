#!/usr/bin/env python3
import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.request
from pathlib import Path


USER_AGENT = "cloudislands-ci-smoke/1.0"


def stable_download_url(project: str, version: str) -> str:
    url = f"https://fill.papermc.io/v3/projects/{project}/versions/{version}/builds"
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=30) as response:
        builds = json.load(response)
    for build in builds:
        if build.get("channel") == "STABLE":
            downloads = build.get("downloads", {})
            server = downloads.get("server:default", {})
            download_url = server.get("url")
            if download_url:
                return download_url
    raise RuntimeError(f"no stable {project} build found for {version}")


def download(url: str, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists() and target.stat().st_size > 0:
        return
    tmp = target.with_suffix(target.suffix + ".tmp")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=120) as response, tmp.open("wb") as out:
        shutil.copyfileobj(response, out)
    tmp.replace(target)


def prepare_paper(work_dir: Path, plugin: Path) -> list[str]:
    cloudislands_dir = work_dir / "plugins" / "CloudIslands"
    config_v2_dir = cloudislands_dir / "config-v2"
    cloudislands_dir.mkdir(parents=True, exist_ok=True)
    config_v2_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(plugin, work_dir / "plugins" / plugin.name)
    (work_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (work_dir / "server.properties").write_text(
        "\n".join(
            [
                "online-mode=false",
                "enforce-secure-profile=false",
                "server-port=25580",
                "enable-query=false",
                "enable-rcon=false",
                "spawn-protection=0",
                "view-distance=2",
                "simulation-distance=2",
                "motd=CloudIslands smoke",
                "",
            ]
        ),
        encoding="utf-8",
    )
    (config_v2_dir / "runtime.yml").write_text(
        "\n".join(
            [
                "node:",
                "  role: LOBBY",
                "  id: smoke-lobby",
                "  pool: lobby",
                "  velocity-server-name: Lobby",
                "  reject-default-identity: false",
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
    return ["java", "-Xms256m", "-Xmx768m", "-jar", "server.jar", "--nogui"]


def prepare_velocity(work_dir: Path, plugin: Path) -> list[str]:
    (work_dir / "plugins" / "cloudislands").mkdir(parents=True, exist_ok=True)
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
    (work_dir / "plugins" / "cloudislands" / "config.yaml").write_text(
        "\n".join(
            [
                "plugin:",
                "  debug: false",
                "core-api:",
                "  base-url: http://127.0.0.1:9",
                "  auth-token: smoke",
                "  admin-token: smoke",
                "  timeout-ms: 100",
                "routing:",
                "  default-lobby: lobby",
                "  fallback-on-failure: lobby",
                "health:",
                "  enabled: false",
                "security:",
                "  require-modern-forwarding: false",
                "  forwarding-secret: smoke",
                "",
            ]
        ),
        encoding="utf-8",
    )
    return ["java", "-Xms256m", "-Xmx512m", "-jar", "server.jar"]


def wait_for_smoke(process: subprocess.Popen, log_path: Path, expected: list[str], ready: list[str], timeout: int) -> None:
    deadline = time.monotonic() + timeout
    seen_expected = set()
    seen_ready = False
    lines = []
    with log_path.open("w", encoding="utf-8") as log:
        while time.monotonic() < deadline:
            line = process.stdout.readline()
            if line:
                lines.append(line)
                log.write(line)
                log.flush()
                for marker in expected:
                    if marker in line:
                        seen_expected.add(marker)
                if any(marker in line for marker in ready):
                    seen_ready = True
                if seen_ready and len(seen_expected) == len(expected):
                    return
            elif process.poll() is not None:
                break
            else:
                time.sleep(0.1)
    tail = "".join(lines[-80:])
    raise RuntimeError(f"server smoke failed; expected={expected} seen={sorted(seen_expected)} ready={seen_ready}\n{tail}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", choices=["paper", "velocity"], required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--plugin", required=True)
    parser.add_argument("--work-dir", required=True)
    parser.add_argument("--cache-dir", required=True)
    parser.add_argument("--timeout", type=int, default=240)
    args = parser.parse_args()

    plugin = Path(args.plugin).resolve()
    work_dir = Path(args.work_dir).resolve()
    cache_dir = Path(args.cache_dir).resolve()
    if not plugin.exists():
        raise RuntimeError(f"plugin jar does not exist: {plugin}")

    download_url = stable_download_url(args.project, args.version)
    server_jar = cache_dir / Path(download_url).name
    download(download_url, server_jar)

    if work_dir.exists():
        shutil.rmtree(work_dir)
    work_dir.mkdir(parents=True)
    shutil.copy2(server_jar, work_dir / "server.jar")

    if args.project == "paper":
        command = prepare_paper(work_dir, plugin)
        expected = ["CloudIslands Paper agent enabled"]
        ready = ["Done ("]
        shutdown = "stop\n"
    else:
        command = prepare_velocity(work_dir, plugin)
        expected = ["CloudIslands Velocity router enabled"]
        ready = ["Done ("]
        shutdown = "end\n"

    env = os.environ.copy()
    env.setdefault("CI_CORE_TOKEN", "smoke")
    env.setdefault("CI_ADMIN_TOKEN", "smoke")
    env.setdefault("VELOCITY_FORWARDING_SECRET", "smoke")
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
        if process.stdin:
            process.stdin.write(shutdown)
            process.stdin.flush()
        try:
            process.wait(timeout=45)
        except subprocess.TimeoutExpired:
            process.terminate()
            process.wait(timeout=20)
    except Exception:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=20)
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
