# CloudIslands

Distributed Skyblock platform for Velocity and Paper networks.

Version: `1.0.1`

CloudIslands treats an island as a global resource, not as a server-bound world.
Island nodes are runtime hosts. Core API owns the state. Velocity owns routing.
Paper nodes run the island when assigned.

No fixed `Island-1` mindset.
No player-facing shard names.
Portable bundles, route tickets, fencing tokens, shared storage.

## What this is

CloudIslands is built to replace SuperiorSkyblock2-style island management in a multi-server setup.

Core idea:

- island data in the control plane
- world bundle in object storage or local fallback
- runtime state in Core API
- Redis as cache, stream, queue helper
- PostgreSQL/MySQL/MariaDB as durable authority
- Velocity routes players with tickets
- Paper nodes activate, save, protect, and unload islands

## Modules

- `cloudislands-api` - public addon API, events, snapshots, service contracts
- `cloudislands-common` - shared policies, config contracts, routing and cache rules
- `cloudislands-protocol` - Core API and agent DTOs
- `cloudislands-core-client` - typed client for Core API
- `cloudislands-core-service` - central service, repositories, job scheduler, admin API
- `cloudislands-velocity` - proxy commands, routing, fallback, ticket handoff
- `cloudislands-paper` - lobby role, island node role, protection, GUI, world runtime
- `cloudislands-storage` - island bundles, manifests, snapshots, restore pipeline
- `cloudislands-migration` - SuperiorSkyblock2 import, dry-run, verification, rollback
- `cloudislands-satis` - optional official Satis feature pack
- `cloudislands-testkit` - fixtures and integration helpers
- `cloudislands-bom` - dependency alignment

## Runtime shape

Typical layout:

- Velocity proxy
- Core API service
- Redis
- PostgreSQL or MySQL or MariaDB
- Object Storage, S3 or MinIO style
- Lobby Paper server
- one or more Island Paper nodes

Island nodes can scale out.
Five or six nodes are fine. More nodes use the same heartbeat and allocator path.

## Island lifecycle

Create:

1. player runs island create
2. Velocity asks Core API
3. Core API opens DB transaction
4. node allocator picks a ready island node
5. job published
6. Paper agent claims the job
7. template restored
8. cell allocated
9. runtime becomes ACTIVE
10. route ticket becomes READY
11. Velocity connects the player
12. Paper consumes the ticket and teleports

Move or recover:

- save portable bundle
- verify manifest and checksum
- mark stale runtime fenced
- pick another node
- restore bundle
- rebuild caches
- issue new route ticket

## Storage and cache

Durable:

- PostgreSQL, MySQL, or MariaDB
- object storage for bundles and snapshots

Fast path:

- L1 local memory cache on Paper and Velocity
- L2 Redis
- L3 SQL database

Redis is not the source of truth.
It helps with cache, streams, locks, heartbeat, and queue work.

## Lifecycle examples

### New island, two island servers

Servers:

- `Lobby-1`
- `Island-A`
- `Island-B`

`Island-A` is already soft full.
`Island-B` is ready.

Flow:

1. player creates an island from the lobby
2. Velocity sends the request to Core API
3. Core API opens a database transaction
4. allocator skips `Island-A`
5. allocator chooses `Island-B`
6. Paper agent on `Island-B` restores the template
7. island runtime becomes `ACTIVE`
8. Velocity connects the player
9. player sees the island, not the server name

Server detail stays internal.

### Existing island opens on another server

Servers:

- `Island-A`
- `Island-B`
- `Island-C`

The island was last active on `Island-A`.
Later, `Island-A` is draining.

Flow:

1. player runs island home
2. Core API sees the island is inactive
3. allocator ignores draining nodes
4. `Island-B` is selected
5. bundle is restored from storage
6. runtime ownership moves to `Island-B`
7. route ticket points Velocity to `Island-B`
8. player lands at the island home

The island is not owned by `Island-A`.
It is owned by the control plane.

### Server failure and recovery

Servers:

- `Island-A`
- `Island-B`
- `Island-C`

`Island-A` dies while islands are active.

Flow:

1. heartbeat expires
2. Core API marks `Island-A` as `DOWN`
3. new routes to `Island-A` stop
4. affected islands become `RECOVERY_REQUIRED`
5. players fall back to lobby
6. Core API checks the latest verified snapshot
7. safe islands restore on `Island-B` or `Island-C`
8. unsafe islands become `QUARANTINED`

No blind restore.
No stale node writes.

### Satis state follows the island

Servers:

- `Island-A`
- `Island-B`

Satis machines are running on an island.
The island moves from `Island-A` to `Island-B`.

Flow:

1. Satis state is saved by island UUID
2. CloudIslands saves the portable island bundle
3. Core API selects `Island-B`
4. `Island-B` restores the island
5. Satis reads the active world and center from CloudIslands API
6. machine and resource-node state remaps to the new runtime
7. tickers start after state hydration

State follows the island.
Not the server.

## Satis feature pack

`cloudislands-satis` is optional.

It can run as an official addon or built-in-compatible feature pack.
The preferred shape is addon mode.

Feature gates:

- machines
- resource nodes
- contracts
- research
- market
- storage
- GUI
- placeholders
- lifecycle hooks
- migration

Disabled means disabled:

- no command registration
- no GUI entry
- no listener
- no ticker
- no unnecessary writes

Satis state is scoped by CloudIslands island UUID.
If an island moves from server A to server B, Satis state follows the island.

## SuperiorSkyblock2 migration

SuperiorSkyblock2 is migration input only.
Not runtime authority.
No live provider hook.

Supported admin flow:

```text
/ciadmin migrate-superiorskyblock2 scan
/ciadmin migrate-superiorskyblock2 dryrun
/ciadmin migrate-superiorskyblock2 import
/ciadmin migrate-superiorskyblock2 verify
/ciadmin migrate-superiorskyblock2 rollback
```

The import path uses dry-run, conflict reports, approval token, bundle creation, checksum verification, and activation test.

## Failure behavior

Node down:

- heartbeat timeout
- node marked DOWN
- new routes blocked
- active islands marked recovery-required
- players fall back to lobby
- Core API checks latest snapshot
- restore elsewhere or quarantine

Core API down:

- loaded islands can keep running
- control-plane writes restricted
- new route and activation limited

Redis down:

- DB direct degraded mode
- slower cache path
- delayed event propagation
- no permanent state loss from Redis alone

Object Storage down:

- active islands stay local
- new activation and snapshot work restricted
- failed saves queued for retry

## Security baseline

- Velocity modern forwarding
- Paper backends behind firewall
- `online-mode=false` only behind Velocity
- forwarding secret required
- Core API token or mTLS
- private Redis, database, and object storage
- scoped admin permissions
- audit log
- plugin messaging minimized

## Build

Requires Java 21.

```bash
./gradlew build
```

## Supported runtime matrix

CloudIslands is not intended to stay a single-patch Paper project.
The support direction starts with the Paper 1.21 family and is expected to add
new stable families through explicit adapter and matrix entries.

Current repository state:

<!-- minecraft-version-matrix:start -->
| Target | Compile | Boot smoke | Release | Notes |
|---|---|---|---|---|
| Paper `1.21.x` | `paper121Compile` | `paper121BootSmoke` | release-supported | current paper-api and plugin.yml baseline |
| Paper `26.1.x` | `paper261Compile` | pending official Paper build | experimental compile-only | adapter compile-verified; boot smoke waits for official Paper build |
| Paper `26.2.x` | `paper262Compile` | pending official Paper build | experimental compile-only | adapter compile-verified; boot smoke waits for official Paper build |
<!-- minecraft-version-matrix:end -->

Velocity `3.5.0-SNAPSHOT` remains the proxy compile baseline with a boot smoke
task; routing integration is tracked separately from the Minecraft matrix.

Status terms:

- `compile baseline`: the project compiles against this API.
- `compile-only`: source compatibility is checked, but server boot is not proven.
- `boot-verified`: a real Paper or Velocity process starts and loads the plugin.
- `integration-verified`: real external services and multi-component behavior are exercised.

The build generates Paper compile and boot smoke tasks from
`gradle/minecraft-versions.toml`, including `compileAllMinecraftVersions`,
`bootSmokeAllStableMinecraftVersions`, `verifyMinecraftVersionMatrix`,
`verifyAdapterPackaging`, and `verifyReadmeVersionTable`.

## Feature parity by evidence

Status values are intentionally conservative. `IMPLEMENTED_VERIFIED` requires
tests or generated release evidence. Compile-only 26.x adapter coverage is not
reported as boot or integration verification.

<!-- feature-parity:start -->
| Area | Status | Verified evidence | Limit |
|---|---|---|---|
| lifecycle/templates/homes/warps/visits | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies cross-Core create, job, route, session, consume | 26.1 and 26.2 stay compile-only until official bootable Paper builds are available |
| access/bans/membership/roles/permissions | IMPLEMENTED_VERIFIED | Core API and permission event replay are exercised in tests | third-party permission plugins are integration-status reported, not all boot-verified |
| flags/protection | IMPLEMENTED_VERIFIED | unit verified; real-player destructive-action smoke is not part of CI | runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests |
| ranking/level/worth/block values | IMPLEMENTED_VERIFIED | service-level verified | worth economics beyond configured value calculations are not release-certified |
| upgrades/size/border/biome | PARTIAL | not covered by multi-service smoke | full upgrade economy and live biome/border interaction tests are still needed |
| bank/economy/missions/challenges/generators/limits | PARTIAL | unit verified; external economy/provider runtime coverage is integration-status only | provider-backed economy and mission parity are not fully production-certified |
| chat/logs/reviews | PARTIAL | unit/static verified only | review/moderation parity remains partial |
| snapshots/rollback/migration/recovery | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies recovery restore with shared services | full backup/restore drill remains a release cluster evidence gate |
| Java API/events/addons | IMPLEMENTED_VERIFIED | apiCompatibilityCheck verifies release contract metadata | external addon certification depends on testkit evidence supplied by the addon |
| integrations/localization/GUI | IMPLEMENTED_UNVERIFIED | verifyIntegrationMatrix proves inventory and state reporting, not third-party boot behavior | third-party plugin runtime certification is pending per-plugin boot fixtures |
<!-- feature-parity:end -->

## Release

Current release: `v1.0.1`

Built for the CloudIslands 1.0.1 baseline.

## Project status

Current read: broad early `v1.0.1`.

Good platform direction.
Not a production GA for multi-Core deployment yet.
Closer to RC or limited beta.

### Assessment

| Area | Score | Notes |
|---|---:|---|
| Architecture and domain model | 8/10 | logical islands and runtime nodes are separated |
| Feature scope | 8/10 | routing, snapshots, permissions, economy, missions, ranking |
| Distributed consistency | 4/10 | Redis lock release and local fallback need hardening |
| Security hardening | 4/10 | proxy-trusted auth headers need stricter boundaries |
| Maintainability | 4/10 | large core classes and regex JSON parsing |
| Test and release readiness | 4/10 | many unit tests, not enough real infrastructure verification |

### Strong parts

Global island model.
Portable bundles.
Core-owned lifecycle.
Velocity-owned routing.
Paper-owned runtime.

Module boundaries are clear enough for a platform:

- API
- protocol
- Core client
- Core service
- Paper
- Velocity
- storage
- migration
- testkit
- optional Satis feature pack

Failure handling and observability were considered from the start.
That matters.

### Release blockers before production GA

| Blocker | Status | Current read |
|---|---|---|
| Redis lock unlock must use atomic compare-and-delete | MITIGATED | activation and player creation locks use Lua compare-and-delete |
| Redis outage must not silently fall back to per-process local locks in multi-Core mode | MITIGATED | local fallback is disabled by default, but multi-Core failure drills are still needed |
| Core API auth must not trust client-provided permission headers | MITIGATED | admin permissions are configured server-side |
| mTLS-by-header requires trusted proxy boundaries | MITIGATED | `MtlsHeaderGuard` checks a trusted proxy allowlist |
| production mode must reject non-durable in-memory fallback | MITIGATED | startup validation always blocks non-durable fallback in production |
| route failure cleanup should be one idempotent path | OPEN | needs stronger cross-Core evidence |
| coordinate fallback should fail closed when placement data is missing | OPEN | needs targeted runtime tests |
| Gradle Wrapper, CI, release binaries, checksums | MITIGATED | wrapper, GitHub Actions, dist bundles, and SHA-256 checksums exist |
| SBOM, provenance, vulnerability gate | OPEN | not wired as release gates |
| real PostgreSQL, Redis, MinIO integration | MITIGATED | `ciIntegrationSmoke` runs with these services |
| multi-Core, multi-version boot, API compatibility gate | MITIGATED | matrix tasks, `ciIntegrationSmoke`, release cluster evidence, and `apiCompatibilityCheck` are wired |

### If running now

Safe-ish constraints:

- exactly one Core instance
- Core bound to loopback or private internal network
- reverse proxy strips and rewrites security headers
- admin API only on a private management network
- in-memory fallback disabled
- Redis, database, and object storage not public
- DB backup and island snapshot restore tested separately

### First files to harden

- `RedisActivationLock`
- `RedisPlayerCreationLock`
- `MtlsHeaderGuard`
- `AdminEndpointGuard`
- `RouteTicketConsumer`

Static review only.
No claim of full production certification.
