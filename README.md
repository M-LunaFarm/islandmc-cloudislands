# CloudIslands

CloudIslands is a production-oriented Skyblock platform for Paper and Velocity.
It treats each island as a portable, globally owned resource instead of tying it
to one Minecraft server. Core owns durable state, Paper runs island worlds, and
Velocity routes players with short-lived tickets.

**Current version:** `1.1.255`

CloudIslands supports both of these deployment shapes:

- **Single Paper:** one public Paper server, Core, PostgreSQL, and private Redis.
  No Velocity or separate lobby server is required.
- **Distributed network:** Velocity, lobby Paper, multiple island Paper nodes,
  two Core instances, PostgreSQL, Redis, and S3-compatible object storage.

The supplied Docker Compose stacks are the fastest supported way to evaluate or
operate either topology.

## Contents

- [Why CloudIslands](#why-cloudislands)
- [Requirements](#requirements)
- [Supported Minecraft versions](#supported-minecraft-versions)
- [Choose a deployment](#choose-a-deployment)
- [Single Paper quickstart](#single-paper-quickstart)
- [Distributed quickstart](#distributed-quickstart)
- [Configuration](#configuration)
- [First validation](#first-validation)
- [Player and operator commands](#player-and-operator-commands)
- [Architecture](#architecture)
- [Reliability and recovery](#reliability-and-recovery)
- [Security](#security)
- [Backup and restore](#backup-and-restore)
- [SuperiorSkyblock2 migration](#superiorskyblock2-migration)
- [Integrations and addons](#integrations-and-addons)
- [Development and release](#development-and-release)
- [Troubleshooting](#troubleshooting)
- [Verified feature coverage](#verified-feature-coverage)
- [Release notes](#release-notes)

## Why CloudIslands

CloudIslands separates logical island ownership from the server currently
hosting its chunks.

- Islands can activate on any compatible ready node.
- Portable bundles include manifests and SHA-256 checksums.
- Fencing tokens prevent stale nodes from committing lifecycle results.
- Route tickets are player-, node-, nonce-, and expiry-bound.
- Core persists islands, membership, economy, permissions, missions, rankings,
  snapshots, jobs, and audit data in SQL.
- Redis accelerates queues, events, locks, and caches but is not the durable
  source of truth.
- Paper protection uses local indexed state on synchronous event paths; it does
  not perform HTTP, SQL, or Redis calls while deciding block events.
- Failed storage saves remain retryable while already loaded islands continue
  local play.
- A single Paper deployment uses the same lifecycle engine and consumes route
  tickets locally.

Portable means CloudIslands-owned world and island state. Third-party database
rows, CoreProtect history, WorldEdit undo history, and other provider-owned
state are not silently embedded in an island bundle.

## Requirements

### Runtime

| Component | Required baseline |
|---|---|
| Paper | `1.21.x` or stable `26.1.x` |
| Java | Java 21 for Paper `1.21.x`; Java 25 for Paper `26.1.x` and `26.2.x` |
| Velocity | `3.5.0-SNAPSHOT` compile baseline for distributed deployments |
| Database | PostgreSQL 16 recommended; MySQL and MariaDB are supported |
| Redis | Redis 7 recommended for Core queues, events, locks, and caches |
| Storage | S3-compatible shared storage for a cluster; local filesystem for one host |
| Build | Gradle Wrapper 9.1; no system Gradle installation required |

### Host sizing

Capacity depends on island count, view distance, plugins, and automation load.
Start small, observe MSPT and heap, then tune each service independently. The
example stacks intentionally use modest development defaults; they are not a
universal production sizing recommendation.

### Before public traffic

- Use durable named volumes or external managed services.
- Keep Core, SQL, Redis, object storage, and clustered Paper backends private.
- Use unique node IDs and Velocity backend names.
- Back up SQL and island storage together.
- Run the validation and release gates documented below.
- Test with the exact economy, permissions, custom-block, and stacker plugins
  used by the target server.

## Supported Minecraft versions

The universal Paper artifact packages explicit runtime adapters. Stable support
requires compile, boot-smoke, packaging, and release-gate evidence; a successful
compile alone is not reported as production support.

<!-- minecraft-version-matrix:start -->
| Target | Compile | Boot smoke | Release | Notes |
|---|---|---|---|---|
| Paper `1.21.x` | `paper121Compile` | `paper121BootSmoke` | release-supported | current paper-api and plugin.yml baseline |
| Paper `26.1.x` | `paper261Compile` | `paper261BootSmoke` | release-supported | stable Paper 26.1.2 API compile and boot verified on Java 25 |
| Paper `26.2.x` | `paper262Compile` | `paper262BootSmoke` | experimental boot-verified | official Paper 26.2 beta build 60 API compile and boot verified on Java 25; stable release channel pending |
<!-- minecraft-version-matrix:end -->

`gradle/minecraft-versions.toml` is authoritative. Run
`./gradlew verifyReadmeVersionTable verifyMinecraftVersionMatrix` after changing
the matrix.

## Choose a deployment

| Deployment | Use it when | Public entrypoint | Island storage |
|---|---|---|---|
| Single Paper | One Minecraft server is enough | Paper `25565` | Durable local path or S3 |
| Distributed Compose | You need lobby separation, HA Core, or multiple island nodes | Velocity `25565` | Shared S3/MinIO |
| Helm | You already operate Kubernetes and externalize persistence correctly | Velocity Service | Shared object storage |
| Manual config pack | Existing services and process supervision are already in place | Paper or Velocity | Local or shared S3 |

Deployment examples live under `deploy/examples`; the full local cluster is in
`deploy/compose`, and the Kubernetes chart is in `deploy/helm/cloudislands`.

## Single Paper quickstart

This stack starts PostgreSQL, private Redis, Core, and one public Paper server.
The current Compose defaults use Paper 26.1.2 images. Paper performs island
activation, protection, commands, GUI, saving,
restore, and local ticket consumption. Redis remains enabled for Core but is
disabled inside the Paper plugin.

### 1. Prepare configuration

```bash
cd deploy/examples/single-paper
cp .env.example .env
mkdir -p secrets
umask 077
openssl rand -hex 32 > secrets/database-password
openssl rand -hex 32 > secrets/core-token
openssl rand -hex 32 > secrets/admin-token
mkdir -p /srv/cloudislands/islands-storage
```

Edit `.env` and set `CLOUDISLANDS_STORAGE_PATH` to the absolute durable path
created above. Keep these production defaults:

```dotenv
CLOUDISLANDS_PAPER_ONLINE_MODE=true
CLOUDISLANDS_PAPER_VERSION=26.1.2
MINECRAFT_EULA=TRUE
```

Setting `MINECRAFT_EULA=TRUE` confirms that you accept the Minecraft EULA.

### 2. Start the stack

```bash
docker compose up -d --build --wait
docker compose ps
```

Join `localhost:25565` unless `CLOUDISLANDS_PAPER_PORT` was changed.

### 3. Validate it

```bash
curl --fail http://127.0.0.1:8443/ready
docker compose exec paper curl --fail --silent http://127.0.0.1:8789/health
docker compose logs --tail=200 core paper
```

In game:

```text
/is create default
/is home
/ciadmin setup verify
/ciadmin doctor
```

Create an island, leave it, reconnect, and run `/is home`. The direct-local
runtime stores the last island marker and returns stale unloaded-island logins
to the configured fallback world instead of applying island coordinates to the
default world.

### 4. Stop without deleting data

```bash
docker compose down
```

Do not add `-v` unless you intentionally want to delete PostgreSQL, Redis, and
Paper volumes. The directory in `CLOUDISLANDS_STORAGE_PATH` is outside those
volumes and must be backed up separately.

## Distributed quickstart

The clustered stack starts PostgreSQL, password-protected Redis, MinIO, two Core
instances behind HAProxy, Velocity, one lobby Paper, and two island Paper nodes.
Only Velocity is publicly exposed. Core is published on loopback for health and
operator access; the backend services and Paper nodes have no public host port.

### 1. Create secrets

From the repository root:

```bash
mkdir -p /srv/cloudislands/secrets
umask 077
openssl rand -hex 32 > /srv/cloudislands/secrets/database-password
openssl rand -hex 32 > /srv/cloudislands/secrets/redis-password
openssl rand -hex 20 > /srv/cloudislands/secrets/storage-access-key
openssl rand -hex 32 > /srv/cloudislands/secrets/storage-secret-key
openssl rand -hex 32 > /srv/cloudislands/secrets/core-token
openssl rand -hex 32 > /srv/cloudislands/secrets/admin-token
openssl rand -base64 48 | tr -d '\n' > /srv/cloudislands/secrets/forwarding-secret
```

Export the file paths:

```bash
export CLOUDISLANDS_DATABASE_PASSWORD_FILE=/srv/cloudislands/secrets/database-password
export CLOUDISLANDS_REDIS_PASSWORD_FILE=/srv/cloudislands/secrets/redis-password
export CLOUDISLANDS_STORAGE_ACCESS_KEY_FILE=/srv/cloudislands/secrets/storage-access-key
export CLOUDISLANDS_STORAGE_SECRET_KEY_FILE=/srv/cloudislands/secrets/storage-secret-key
export CLOUDISLANDS_CORE_TOKEN_FILE=/srv/cloudislands/secrets/core-token
export CLOUDISLANDS_ADMIN_TOKEN_FILE=/srv/cloudislands/secrets/admin-token
export CLOUDISLANDS_FORWARDING_SECRET_FILE=/srv/cloudislands/secrets/forwarding-secret
export MINECRAFT_EULA=TRUE
```

The forwarding secret must be shared by Velocity and every clustered Paper
backend. Never reuse it as the Core or admin token.

### 2. Start the cluster

```bash
docker compose -f deploy/compose/docker-compose.yml up -d --build --wait
docker compose -f deploy/compose/docker-compose.yml ps
```

The default client address is `localhost:25565`.

### 3. Validate routing capacity

```bash
curl --fail http://127.0.0.1:8443/live
curl --fail http://127.0.0.1:8443/ready
docker compose -f deploy/compose/docker-compose.yml logs --tail=200 core-1 core-2 velocity lobby-paper island-paper-a island-paper-b
```

`/ready` must report durable database, Redis, object storage, queue, and fresh
island-node heartbeat checks as ready. The default topology should expose two
route candidates before public traffic reaches Velocity.

Run an end-to-end player smoke:

```text
/is create default
/is home
/is visit <player-or-island>
/ciadmin node list
/ciadmin doctor
```

### 4. Scale island nodes

Every island Paper process needs:

- a unique `node.id`;
- a unique Velocity server name matching the proxy backend entry;
- the same island pool when it should serve the same allocation group;
- the same Core endpoint, storage bucket, and forwarding secret;
- a distinct writable Paper data directory.

Never clone a live Paper data directory between running nodes. Island ownership
comes from Core and storage, not from copying one server's local world state.

## Configuration

CloudIslands uses Config v2 YAML. Bundled defaults are materialized under the
plugin data directory on first start.

### Config packs

| Path | Purpose |
|---|---|
| `deploy/examples/single-paper/config-pack.yml` | One public Paper server with direct-local routing |
| `deploy/examples/single-node/config-pack.yml` | One clustered island node |
| `deploy/examples/two-island-nodes/config-pack.yml` | Two-node routing and capacity example |
| `deploy/examples/production-ha/config-pack.yml` | HA-oriented production baseline |
| `deploy/examples/migration-lab/config-pack.yml` | Isolated SuperiorSkyblock2 migration rehearsal |

### Paper configuration groups

- `runtime.yml`: node identity, role, pool, capacity, heartbeat, and health.
- `integrations.yml`: Core endpoint, Redis, storage, routing mode, and hooks.
- `security.yml`: Core/admin tokens, forwarding, route sessions, and proxies.
- `features.yml`: GUI and feature switches.
- `gameplay.yml`: generators, protection, limits, and gameplay policy.
- `ui/`: localized messages, theme, and menu definitions.

Use `ISLAND_NODE` for servers that host island worlds. Use `LOBBY` for a
clustered lobby that provides commands and GUI without activation or saving.

For single Paper, the important values are:

```yaml
redis:
  enabled: false
routing:
  direct-local-teleport: true
  local-fallback-world: world
forwarding:
  required: false
route-session:
  enforce: false
  required: false
```

For clustered Paper, keep direct-local routing disabled and require Velocity
modern forwarding, a route session, and a proxy-source boundary.

### Core persistence

Production mode rejects unsafe in-memory authority. Use PostgreSQL, MySQL, or
MariaDB with JDBC fallback disabled. Automatic schema creation is explicit and
serialized across Core instances. Applied migrations are checksum-tracked;
modified migration history or incompatible critical columns fail startup.

Use one migration leader with automatic schema enabled and start additional
Core instances with automatic schema disabled after the schema is ready. The
supplied distributed Compose stack already follows this pattern.

### Secrets

Prefer Docker/Kubernetes secret files or a host secret manager. Do not commit
tokens, passwords, access keys, forwarding secrets, `.env` files, or generated
runtime configuration containing resolved secrets.

### Helm

The chart is under `deploy/helm/cloudislands`. Set an existing Secret containing
the keys configured under `secrets.*`, pin every image tag, provide durable
storage classes, and use at least two Core replicas for HA. The chart defaults
are a starting point, not a substitute for network policies, TLS, backups, and
pod disruption planning.

## First validation

Treat a healthy process as necessary but insufficient. Validate the user flow
and persistence path.

1. Confirm Core `/live` and `/ready` are `UP`.
2. Confirm every expected Paper node appears in `/ciadmin node list`.
3. Run `/ciadmin setup verify` and `/ciadmin doctor`.
4. Create an island and verify the player reaches its generated spawn.
5. Disconnect and run `/is home` after reconnecting.
6. Create a snapshot, deactivate the island, and restore it.
7. Restart Paper and confirm the node returns from `STARTING` to `READY`.
8. Rehearse a storage outage and verify active local play remains available.
9. Inspect Core, Paper, and Velocity health endpoints and logs for retries or
   stale-node failures.
10. Back up and restore a non-production island before onboarding players.

The repository release gates cover these contracts, but operators must still
exercise their own network, plugins, permissions, economy, and storage.

## Player and operator commands

### Player entrypoints

- `/is`, `/island`, `/섬`: island menu and player commands.
- `/is help`: paginated command help for the enabled feature set.
- `/is create [template]`, `/is home`, `/is visit`, `/is warp`: lifecycle and routing.
- `/is members`, `/is invite`, `/is trust`, `/is permissions`: team access.
- `/is bank`, `/is warehouse`, `/is upgrades`, `/is missions`: progression.
- `/is settings`, `/is fly`, `/is biome`, `/is border`: island environment.
- `/is snapshot`, `/is restore`: owner-facing recovery where permitted.

Commands are permission-gated under `cloudislands.island.*`. The base
`cloudislands.player` permission defaults to players; mutation permissions can
be restricted by the server permission provider.

### Operator entrypoints

- `/ciadmin status`: compact service and node state.
- `/ciadmin setup verify`: deployment wiring validation.
- `/ciadmin doctor`: first-line health and recovery guidance.
- `/ciadmin node ...`: list, inspect, drain, undrain, move, and safe shutdown.
- `/ciadmin island ...`: inspect, activate, deactivate, save, restore, repair,
  quarantine, migrate, or delete.
- `/ciadmin jobs`, `/ciadmin route`, `/ciadmin storage`: control-plane diagnosis.
- `/ciadmin audit`, `/ciadmin metrics`, `/ciadmin support-bundle`: evidence and observability.
- `/ciadmin integrations report`: optional plugin adapter status.
- `/ciadmin migrate-superiorskyblock2 ...`: migration workflow.

Paper permissions use `cloudislands.admin.*`. Core separately enforces
server-side admin-token permissions. Granting a Bukkit permission does not
bypass the Core admin policy.

## Architecture

```text
Players
   |
   +--> Single Paper --------------------------+
   |         |                                 |
   |         +-- local route-ticket consume    |
   |                                           v
   +--> Velocity --> Lobby / Island Paper --> Core API
                                               |   |   |
                                               |   |   +--> Redis
                                               |   +------> SQL authority
                                               +----------> island storage
```

### Module map

| Module | Responsibility |
|---|---|
| `cloudislands-api` | Public addon API, events, services, and typed contracts |
| `cloudislands-common` | Shared security, routing, config, failure, and cache policies |
| `cloudislands-protocol` | Wire DTOs and compatibility contracts |
| `cloudislands-core-client` | Typed asynchronous Core client |
| `cloudislands-core-service` | Durable authority, HTTP/admin API, jobs, audit, and allocation |
| `cloudislands-paper` | Commands, GUI, protection, activation, save, restore, and teleport |
| `cloudislands-velocity` | Proxy commands, route preparation, sessions, and transfers |
| `cloudislands-storage` | Bundles, manifests, checksums, snapshots, and retention |
| `cloudislands-migration` | SuperiorSkyblock2 import and verification tooling |
| `cloudislands-satis` | Optional official factory/progression feature pack |
| `cloudislands-testkit` | Addon and integration fixtures |
| `cloudislands-bom` | Developer dependency alignment |

### Island lifecycle

1. A player requests create, home, visit, or warp.
2. Core validates permissions and locks the logical island transition.
3. The allocator selects a fresh compatible node with available capacity.
4. Core publishes a fenced job.
5. Paper claims the job, restores or creates the island cell, and preloads it.
6. Paper reports completion with the claim and fencing token.
7. Core commits the active runtime and marks the route ticket ready.
8. Velocity transfers the player, or single Paper consumes the ticket locally.
9. Paper resolves a safe destination within the active island region.

Physical node, world, cell, storage key, and database details stay out of
player-facing messages.

### Data authority

- **SQL:** durable islands, runtime, jobs, members, permissions, economy,
  missions, rankings, snapshots, audit, and idempotency receipts.
- **Object/local storage:** portable island bundles and manifests.
- **Redis:** queues, events, locks, heartbeat/cache acceleration.
- **Paper local disk:** active runtime worlds and retry journals, not cluster authority.

## Reliability and recovery

### Node failure

Core confirms stale heartbeats before declaring a node down. New routes stop,
affected islands enter recovery, and another compatible node restores the
latest verified bundle. Stale completions fail fencing checks.

### Graceful restart

A restarted Paper node explicitly rejoins through `STARTING` and returns to
`READY`; a previous graceful `SHUTTING_DOWN` heartbeat does not permanently
exclude the new process.

### Object storage failure

Active islands remain loaded for local play. New restore/activation work that
needs storage fails closed, and periodic or empty-island save failures remain
queued for retry. Storage health probing runs off the Paper main thread.

### Core failure

Loaded island protection and constrained local play continue. Control-plane
mutations, new activations, and routes remain limited until Core is healthy.

### Redis failure

SQL remains durable authority. Queue/event and cache paths degrade or pause;
they must not silently turn into divergent per-process production authority.

### Player reconnect safety

Single Paper records the last active island in player persistent data. If Paper
loads that player's island coordinates into the fallback world after the shard
was unloaded, CloudIslands compares the marker with the active-island registry
and moves the player to the configured safe fallback spawn.

## Security

### Single Paper

- Keep Paper `online-mode=true` whenever it is directly reachable.
- Publish only the Paper port and loopback operator endpoints.
- Keep Core, SQL, and Redis on a private container or host network.
- Protect Core and admin APIs with separate random tokens.

### Distributed network

- Velocity is the only public Minecraft entrypoint.
- Use modern forwarding with the same strong secret on Velocity and Paper.
- Firewall Paper backends and require route sessions.
- Keep Core, SQL, Redis, and object storage private.
- Terminate TLS at a trusted internal boundary if Core leaves one host.
- Strip spoofable security headers at the proxy boundary.

### Admin API

Core requires both normal API authentication and the admin token for admin
routes. `CI_ADMIN_PERMISSIONS` is the server-side allowlist. Unknown permission
names fail startup, and future permissions are not granted automatically by the
explicit default profile.

### Operational rules

- Never store secrets in Git or logs.
- Never expose Redis, PostgreSQL, MySQL, MariaDB, or MinIO directly to players.
- Never enable in-memory production fallback.
- Never bypass schema checksum or contract failures.
- Treat support bundles and audit exports as sensitive operational data.

## Backup and restore

### Back up together

1. SQL authority.
2. S3 bucket or `CLOUDISLANDS_STORAGE_PATH`.
3. Deployment configuration and secret references.
4. Third-party databases such as CoreProtect or economy providers, separately.

For single Paper, also retain the Paper volume when player inventories and
vanilla player data live there.

### Restore rehearsal

1. Restore SQL to an isolated environment.
2. Restore the matching island storage snapshot.
3. Start Core and verify migration checksums and schema contracts.
4. Start Paper nodes and confirm compatible heartbeats.
5. Restore one island and verify its manifest checksum.
6. Consume a home route and inspect audit evidence.
7. Run `releaseClusterSmokeGate` before declaring the backup usable.

CloudIslands does not treat a CoreProtect rollback or WorldEdit undo history as
an island-bundle restore.

## SuperiorSkyblock2 migration

SuperiorSkyblock2 migration is input-only; CloudIslands has no runtime
dependency on SuperiorSkyblockAPI.

Use an isolated migration lab and follow this order:

1. **Scan** the legacy source and list unsupported or ambiguous data.
2. **Dry-run** owner, member, role, permission, home, warp, economy, mission,
   generator, limit, and world conversions.
3. **Back up** legacy SQL/world data and CloudIslands SQL/storage.
4. **Approve** the exact dry-run report with the generated approval token.
5. **Import** without allowing legacy and CloudIslands writers concurrently.
6. **Verify** Core state, bundles, manifests, checksums, permissions, economy,
   and player routes.
7. **Compare** source and destination counts and exceptions.
8. **Plan rollback** before removing the legacy provider.

Start from `deploy/examples/migration-lab/config-pack.yml`. Migration reports
are runtime artifacts and should not be committed to this repository.

## Integrations and addons

Optional soft integrations include Vault, PlaceholderAPI, LuckPerms,
CoreProtect, WorldEdit, FAWE, ItemsAdder, Oraxen, Nexo, CraftEngine,
RoseStacker, WildStacker, AdvancedSpawners, Plan, ProtocolLib, SkinsRestorer,
SuperVanish, PremiumVanish, SlimeWorldManager, Slimefun, and CMI.

Use `/ciadmin integrations report` to distinguish detected, missing, degraded,
and unsupported adapters. Optional API failures fall back conservatively and
remain observable.

### Custom blocks and stacks

CloudIslands can value and limit custom blocks from supported providers and can
reconcile logical stack amounts. Provider keys use lower-case prefixes such as
`itemsadder:`, `oraxen:`, `nexo:`, `craftengine:`, and `slimefun:`. When several
stacker providers describe the same position, CloudIslands uses the highest
logical amount instead of double counting.

### PlaceholderAPI

The expansion exposes island identity, owner, role, team, co-op, level, worth,
rank, bank, limits, homes, warps, flags, permissions, upgrades, chat state, and
SS2-compatible aliases. Reads are coalesced and cached by island to avoid one
Core request per scoreboard line and player.

### Addon API

External addons can register collision-safe `/is` subcommands through
`AddonIslandCommand`. The API supports permissions, argument bounds,
asynchronous results, tab completion, help integration, and automatic cleanup
when an addon is disabled. Use `cloudislands-testkit` and the example addon as
the compatibility reference.

### Satis

`cloudislands-satis` is optional. Its machines, resource nodes, contracts,
research, market, storage, GUI, and placeholders remain scoped by CloudIslands
island UUID. Disabled features stop their listeners and tickers without purging
stored island data.

## Development and release

### Build

```bash
./gradlew build
```

The wrapper may run on Java 25; Gradle toolchains select the Java version needed
by each compile and boot-smoke target.

Useful gates:

```bash
./gradlew verifyMinecraftVersionMatrix compileAllMinecraftVersions
./gradlew bootSmokeAllStableMinecraftVersions verifyAdapterPackaging
./gradlew apiCompatibilityCheck protocolCompatibilityCheck
./gradlew ciIntegrationSmoke
./gradlew releaseClusterSmokeGate
```

Full local release gate:

```bash
./gradlew build distBundle distChecksums distSbom distProvenance --no-daemon
```

### Release artifacts

Artifacts are generated under `build/dist`:

- `cloudislands-<version>.zip`;
- `cloudislands-addons-<version>.zip`;
- Paper and Velocity plugin jars;
- Core service runtime;
- migration tools and developer kit;
- `checksums-sha256.txt`;
- `cloudislands-sbom.cdx.json`;
- `provenance.json`;
- `CHANGELOG.txt`.

Verify the complete bundle from inside `build/dist`:

```bash
sha256sum -c checksums-sha256.txt
```

### API compatibility

CloudIslands follows semantic versioning for the public addon API. The public
signature baseline, contract metadata, example addon, and testkit are checked
before release. Deprecated API remains available for at least one minor release
before removal.

### Repository documentation policy

Operator documentation belongs in this README. Generated parity, migration,
support-bundle, and smoke reports are runtime/build artifacts rather than
committed Markdown documents.

<!-- operator-release-docs:start -->
## Operator release documentation

### Production setup

Use durable JDBC authority, shared storage, private Redis, authenticated Core,
and explicit server-side admin permissions. A distributed production network
should run at least two Core instances behind an internal health-checked load
balancer. Run `/ciadmin setup verify`, `/ciadmin doctor`, player create/home
smokes, backup/restore rehearsal, `ciIntegrationSmoke`, and
`releaseClusterSmokeGate` before opening traffic.

### Local dev stack

Use the single-Paper Compose example for the smallest complete stack or
`deploy/compose/docker-compose.yml` for the distributed shape. Wait for Core
`/ready`, confirm all Paper heartbeats, then run `/ciadmin setup verify`,
`/ciadmin doctor`, island create, route consume, snapshot, and restore checks.

### Migration procedure

SuperiorSkyblock2 migration must run as scan, dry-run, backup, approval, import,
verify, compare, and rollback planning. Do not run both providers as concurrent
authoritative writers, and do not decommission the source until SQL and bundle
evidence has been reconciled.

### Troubleshooting

Start with `/ciadmin doctor`, then inspect `/ciadmin node list`, `/ciadmin island
inspect`, `/ciadmin route debug`, `/ciadmin storage verify`, Core `/ready`, and
the component health endpoints. Preserve failure codes and support bundles for
operators without exposing node IDs, storage keys, tokens, or database errors
to players.

### Release artifacts and changelog

`./gradlew build distBundle distChecksums distSbom distProvenance` produces the
release bundle, `checksums-sha256.txt`, `cloudislands-sbom.cdx.json`,
`provenance.json`, and generated `CHANGELOG.txt`. A matching `v*` tag runs the
dedicated release workflow and publishes the verified distributable assets.
<!-- operator-release-docs:end -->

## Troubleshooting

### Core `/ready` is down

Read every failed check in the response. Verify SQL connectivity and schema,
Redis authentication, object-storage credentials/bucket, job queue, and fresh
Paper heartbeats. `/live` only proves the process is alive; it does not prove
the deployment can route an island.

### Database schema contract mismatch

Stop writes and back up SQL. Repair every reported `table.column` to the
expected type, then rerun the normal migration/bootstrap path. Do not bypass
the guard; it exists to prevent later island-creation corruption.

### Paper node never becomes ready

Check unique `node.id`, pool, Velocity backend name, Core token, storage access,
supported templates, hard capacity, and heartbeat timestamps. A node in
`DRAINING` intentionally refuses new activations. A restarted graceful node
must send `STARTING` before returning to `READY`.

### Player cannot enter an island

Inspect the route ticket state, target node, expiry, nonce/session publication,
world activation job, and Paper teleport counters. In a cluster, confirm modern
forwarding and the backend firewall. In single Paper, confirm
`direct-local-teleport: true` and the exact `local-fallback-world` name.

### Snapshot or deactivation is stuck

Check storage health, save retry queues, pending snapshot journals, world flush,
chunk unload, manifest generation, and checksum upload. CloudIslands will not
unload an island after a failed required save.

### Redis failures appear in single Paper health

Paper Config v2 must contain `redis.enabled: false`. Core still needs its private
Redis service. Health should show `redisEnabled=false` and no synthetic Paper
Redis failure growth.

### Config reload is rejected

Runtime-safe message and UI changes can reload atomically. Node identity,
storage backend, forwarding, and other process-level changes require a restart;
the active configuration remains unchanged when reload validation fails.

### Need an evidence bundle

Use `/ciadmin support-bundle` and collect Core, Paper, and Velocity health/log
windows around the failure. Redact tokens, player-sensitive data, storage keys,
and private topology before sharing.

## Verified feature coverage

The block below is generated from repository evidence and verified by
`verifyFeatureParityEvidence`. Do not edit it independently of the Gradle gate.

<!-- feature-parity:start -->
| Area | Status | Verified evidence | Limit |
|---|---|---|---|
| lifecycle/templates/homes/warps/visits | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies advisory-lock-serialized dual-Core schema bootstrap on PostgreSQL and MySQL 8.4 plus cross-Core create, job, route, session, consume, player-ticket cache convergence, node recovery, bank, membership, warp, event replay, and database backup behavior; Paper 1.21.11, 26.1.2 stable, and 26.2 build 60 beta smoke verify normal command registration and runtime startup, while Paper 26.1.2 additionally verifies rejected-bootstrap rollback, diagnostic /is and /ciadmin, corrected-config retry, and second-attempt READY recovery; Paper tests verify main-thread template permission preflight, exact initiating-Player fencing for paid creation plus delete/reset feedback, automatic post-charge refund before Core creation when the connection is replaced, UUID/island-name/player-name targeted warp resolution in native and migration commands, exact initiating-Player fencing for home/warp lookup, permission resolution, safe-destination lookup, local teleport, fallback movement, and feedback, one observable warp-to-island-info lookup chain, stale target-info response rejection, Core-authoritative newest-intent revisions for asynchronous primary-island selection across Paper nodes, exact selection-feedback connection fencing, scheduler-bound single-Paper fallback teleport, target-island coordinates, safe destination scans, final online-player revalidation, bounded destination revalidation, teleport warmup cancellation as soon as a player moves or starts falling within the same block, and exact initiating-Player fencing for both player and administrator teleports through route creation, polling, publication, local consumption, world readiness, safe-destination resolution, final teleport, fallback movement, feedback, loading bars, and delayed route-session rejection | 26.2 build 60 beta is compile- and boot-verified but remains experimental and is not release-supported until Paper publishes a stable channel build |
| access/bans/membership/roles/permissions | IMPLEMENTED_VERIFIED | Core API and permission event replay are exercised in tests; accepted visitor bans and kicks return to the Paper scheduler and evict the target independently from actor connectivity, while all delayed member and ban lists, invite creation/list/accept/decline, leave/remove/uncoop, role/trust/ownership changes, pardon, and actor feedback require the exact initiating Player connection; permission and role queries, direct mutations, override resolution, and staged-save success or conflict UI retain that same exact connection, and an older save completion cannot clear equal-valued changes staged by a replacement connection | third-party permission plugins are integration-status reported, not all boot-verified |
| flags/protection | IMPLEMENTED_VERIFIED | unit verified; Paper policy tests and protection smoke cover LOWEST-priority custom-machine right-click fencing through the independent container permission for ItemsAdder/Oraxen/Nexo/CraftEngine/Slimefun blocks, dedicated normal/glow item-frame add, rotate, and remove changes plus HIGHEST-priority pickup attempt and final entity boundaries, granular interactions, durable role-gated personal flight with external-flight ownership isolation, Core-authoritative newest-intent preference revisions, exact-connection callback fencing, and replacement-safe update tokens, durable per-player border visibility, real blue/green/red border color transitions, block-display preferences, transition refresh, and border ownership isolation, soft-explosion target authorization and non-destructive accounting, CraftEngine furniture build/break enforcement, RoseStacker direct-spawn flag parity, default-compatible natural flags, shard-safe player time/weather overrides, fail-closed dispenser, armor-dispense, origin-island-preserving ground items and merges, hopper, inventory-transfer, and block-projectile boundaries including migrating islands, cancellation-final natural spread, growth, formation, fade, fluid, fire, leaf, bucket, fertilize, structure, and Enderman transitions, dependent block breaks, raids, mob targeting, bounded asynchronous safe returns with same-instance and authorizing-block continuation fencing, and fail-closed player/entity cross-dimension portals inside active island regions | runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests; cross-dimension island worlds remain intentionally unavailable until their lifecycle, storage, and routing are implemented end to end |
| ranking/level/worth/bank/block values | IMPLEMENTED_VERIFIED | verifyRankingWorthCertification and verifyIntegrationRuntimeSmoke cover typed values, authoritative bank-balance ordering with ranking exclusions, ItemsAdder/Oraxen/Nexo/CraftEngine/Slimefun custom block and furniture identity, CraftEngine place/break event deltas, RoseStacker/WildStacker/AdvancedSpawners logical amounts, cause-aware permanent entity removal including external plugin removals, cancellation-final and inheritance-deduplicated block transitions, chunk-complete UUID-deduplicated entity snapshots, bounded scans, serialized writes, and concurrent-mutation rejection; Paper policy tests verify every delayed progression query and level-recalculation result returns only to the exact initiating Player connection instead of a same-UUID replacement | custom and stacker vendor APIs remain deployment-specific live acceptance; busy islands retry reconciliation instead of publishing a mixed-time scan |
| upgrades/size/border/biome | IMPLEMENTED_VERIFIED | verifyUpgradeEffectCoverage covers Core upgrade effects, atomic multi-price charging/refunds, rule-complete GUI views, and biome normalization; one level now preserves and applies concurrent size/team/warp/coop/role limits, crop/spawner/drop multipliers, island effects, normal-world per-material generator rates, arbitrary block limits, and per-entity limits from either CloudIslands or quoted-level SS2 layouts without reducing administrator or mission overrides; Paper enforces both generator upgrade-level and authoritative island-level rule requirements, with fail-closed level loading and event-driven cache refresh, plus exact material and entity-type counts including logical stacker spawns alongside existing aggregate limits; authoritative size is carried through activation, restore, reset, and migration jobs, while live size changes atomically replace Paper protection, scan, and snapshot bounds; Core island response paths expose the independent authoritative BORDER limit, and async border/profile responses return through the Paper scheduler only when the exact initiating Player connection, current island, and latest per-player request revision still match; Paper tests also cover reconnect, island-change, and out-of-order border rejection, region-file cell isolation, unsafe-size fencing, world-border policy, activation-time persisted-biome reconciliation, and chunk-batched biome painting | normal-world per-material generator rates, arbitrary block limits, and per-entity limits are runtime-applied; Nether and End generator-rate maps remain preserved but intentionally inactive until cross-dimension island lifecycle, storage, and routing exist end to end; operator deployment acceptance is still recommended; cells below 1024 blocks or not aligned to 512 blocks fail startup, and islands that cannot fit without sharing region files fail activation or are fenced on unsafe live resize |
| bank/economy/missions/challenges/generators/limits | IMPLEMENTED_VERIFIED | verifyMissionEventProgress covers final uncancelled block, farm, kill, fishing, capacity-bounded bulk crafting, enchanting, statistic, advancement, and item-consumption progress plus the bounded definition cache; gameplay progress delivery carries Core idempotency metadata, while monotonic absolute progress keeps repeated authoritative bank balances and island levels from double-counting or regressing; level recalculation advances the built-in level mission; PostgreSQL and MySQL dual-Core smoke verifies same-key MISSION and CHALLENGE definitions retain independent rows and progress, including fresh and upgraded MySQL schemas, restored mission metadata, and MySQL-safe completion assignment order; reward-settlement tests cover failure reopening, repeatable reset, and durable warehouse item delivery; PostgreSQL/MySQL shared warehouse settlement records move through PREPARED and ESCROWED before Paper replays the exact mutation key, so reconnecting on another Paper node can resume protected deposits and withdrawals; every delayed warehouse stage retains the exact initiating Player instance, and a replacement connection can continue only by replaying the durable PDC/Core settlement instead of inheriting an older inventory callback; `/is deposit *` and `/is withdraw *` resolve authoritative full balances through scheduler-safe Vault and Core queries before reusing the existing idempotent mutation, refund, and rollback paths; delayed balance, target, deposit, withdrawal, rollback, refund, upgrade-purchase, and mission-completion results retain the exact initiating Player connection before Paper feedback; Paper warehouse policy rejects metadata-bearing items that its material-and-amount schema cannot restore, while overflow-safe logical-stack mob-drop scaling, upgrade CAS/refund, generator, and economy safety gates cover the remaining scope | brewing completion has no reliable Bukkit actor and is intentionally not guessed; operator live-server economy/provider acceptance is still recommended |
| chat/logs/reviews | IMPLEMENTED_VERIFIED | verifyReviewModerationCoverage plus current-visible-visitor classification, Core audit/visitor route tests, UUID/island-name/player-name review target resolution, exact-connection fencing for current visitor, public-island, review, and visitor-stat reads plus review writes/deletes, same-island continuation for island-scoped query results, LOWEST/HIGHEST mutually exclusive local/team-chat isolation, MONITOR-only accepted global-spy delivery, scheduler-bound permission and message calls, and same-instance reconnect fencing for both queued dispatch and delayed Core failure feedback cover current workflow | live multi-player chat moderation acceptance is deployment-specific outside unit CI |
| snapshots/rollback/migration/recovery | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies recovery restore with shared services; Paper policy tests verify delayed snapshot list/create/restore responses cross the scheduler and retain the exact initiating Player connection instead of a same-UUID replacement, while migration return tickets retain the initiating Player instance across polling, route-session publication, proxy transfer, failure feedback, and delayed BossBar cleanup | releaseClusterSmokeGate now includes database backup, object bundle, manifest checksum, restore, route, and audit evidence |
| Java API/events/addons | IMPLEMENTED_VERIFIED | apiCompatibilityCheck verifies release contract metadata and the public API signature baseline; Paper tests fence asynchronous addon command results to the originating plugin lifecycle and same online Player instance, with scheduler-only delivery and no disable-time completion-thread fallback | external addon certification depends on testkit evidence supplied by the addon |
| integrations/localization/GUI | IMPLEMENTED_VERIFIED | verifyIntegrationRuntimeSmoke verifies executable runtime services including CraftEngine block/furniture and Slimefun block identity plus RoseStacker, WildStacker, and AdvancedSpawners logical amount reconciliation; Paper tests also verify formatting-only MiniMessage rendering with literal dynamic placeholders across branding, GUI, scoreboard, command, title, action-bar, boss-bar, kick, migration, routing, boundary, flag, and protection-notice components, while all revision-guarded GUI loaders and asynchronous join-profile responses reject disconnected or replaced Player instances before mutating presentation or flight state, the async admin node loader reserves its GUI revision before the Core request so older responses cannot replace a newer menu, shared async admin success and failure feedback retains the initiating Player connection, and the shared GUI click boundary rejects null and AIR slots before resolving actions; Paper 26.1.2 smoke proves atomic config reload by applying message changes to already-created renderers and refusing node changes as restart-required without mutating active runtime | Vault, PlaceholderAPI, Plan, vanish, ItemsAdder/Oraxen/Nexo/CraftEngine/Slimefun custom-content, and stacker accounting services are executable; click, URL, insertion, selector, score, and NBT MiniMessage tags stay intentionally disabled as an untrusted-format security boundary; CoreProtect remains append-only and WorldEdit/FAWE remain compatibility-only because CloudIslands chunk bundles own world-state transfer |
<!-- feature-parity:end -->

## Release notes

Current release: `v1.1.255`

Release notes for `v1.1.255`:

- Supports a complete single-Paper topology with PostgreSQL, private Core
  Redis, local filesystem island storage, direct-local ticket consumption, and
  a public online-mode Paper server.
- Routes players immediately after island creation and restores inactive island
  snapshots through the same ticket and safe-teleport pipeline.
- Rejoins a gracefully restarted Paper node through `STARTING` instead of
  preserving stale `SHUTTING_DOWN` state.
- Probes storage health asynchronously so S3 outages cannot block the Paper
  watchdog or server thread; active islands stay playable and failed saves retry.
- Persists a last-island marker and recovers stale unloaded-shard logins to the
  configured fallback spawn.
- Honors `redis.enabled: false` in Paper Config v2 and reports single-Paper
  routing and authentication policies accurately in health output.
- Retains exact player-connection fencing across delayed routing, membership,
  permissions, progression, review, inventory, GUI, and operator feedback.
- Keeps the public API compatible with the 1.0.x signature baseline and ships a
  universal Paper artifact for the verified runtime matrix.

## Project status

The current `1.1.255` source baseline is implemented and verified for practical
single-Paper and distributed use. Repository evidence includes unit and policy
tests, real PostgreSQL/Redis/object-storage integration, multi-Core behavior,
Paper and Velocity boot smokes, real player create/home/restore flows, node
restart, storage fault injection, bundle/checksum generation, SBOM, provenance,
and Compose rendering.

Deployment-specific acceptance remains mandatory. Before a public launch,
repeat the smoke paths with the production network, storage provider, database,
permission/economy plugins, custom content, view distance, and expected player
load.

## Critical paths

- `deploy/examples/single-paper/docker-compose.yml`: smallest complete runtime.
- `deploy/compose/docker-compose.yml`: distributed local/host topology.
- `deploy/helm/cloudislands`: Kubernetes chart.
- `gradle/minecraft-versions.toml`: supported Minecraft matrix.
- `build/dist`: release bundle output.
- `cloudislands-paper/src/main/resources/config-v2`: Paper configuration defaults.
- `cloudislands-velocity/src/main/resources/config-v2`: Velocity configuration defaults.
- `cloudislands-core-service/src/main/resources`: Core runtime resources and migrations.

Repository: <https://github.com/M-LunaFarm/islandmc-cloudislands>
