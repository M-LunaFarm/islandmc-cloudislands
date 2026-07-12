# CloudIslands

Distributed Skyblock platform for Velocity and Paper networks.

Version: `1.1.123`

CloudIslands treats an island as a global resource, not as a server-bound world.
Island nodes are runtime hosts. Core API owns the state. Velocity owns routing.
Paper nodes run the island when assigned.

No fixed `Island-1` mindset.
No player-facing shard names.
Portable bundles, route tickets, fencing tokens, shared storage.

Portable means CloudIslands-owned world and island state. Third-party databases,
audit history, undo queues, and other plugin-owned state are not implicitly
embedded in an island bundle.

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

## Documentation map

This repository keeps operator documentation in this README because the result
publication policy excludes committed markdown documents other than the root
README.

<!-- operator-release-docs:start -->
## Operator release documentation

CloudIslands publishes operator documentation through this README and release
artifacts through `build/dist`. Do not add committed Markdown runbooks outside
this file.

### Production setup

Production networks need private Core API, Redis, SQL, object storage,
Velocity, lobby Paper, and island Paper nodes on a trusted internal network.
Run at least two Core instances behind an internal load balancer, keep SQL and
object storage durable, disable in-memory production fallback, and scope
`cloudislands.admin.*` permissions to operators. Before public traffic, run
`./gradlew ciIntegrationSmoke`, then rehearse backup, restore, route handoff,
and audit evidence with `releaseClusterSmokeGate`.

### Local dev stack

For local development, start Redis, SQL, MinIO-compatible object storage, and
Core API first. Start Velocity after Core health is ready, then start one lobby
Paper server and at least one island Paper node with unique `node.id` values,
matching Velocity backend names, `/ciadmin setup verify`, `/ciadmin doctor`, and
a create-route-consume smoke path.

### Migration procedure

SuperiorSkyblock2 migration is ordered: scan, dry-run, backup, approval token,
import, verify, compare, and rollback planning. Dry-run output must make owner,
world, economy, home, warp, mission, permission, and unsupported-feature losses
visible before import. Rollback must verify both Core state and storage bundle
state before the legacy provider is removed.

### Troubleshooting

Use `/ciadmin doctor` first for Core, Redis, DB, storage, Velocity, Paper node,
route ticket, template checksum, integration, and migration-lock health. Use
`/ciadmin route debug`, `/ciadmin island inspect`, `/ciadmin storage verify`,
and support bundles for targeted failures. Player-facing messages must avoid
internal node IDs, storage keys, and database errors; operator views carry the
failure code and recovery command.

### Release artifacts and changelog

Release builds attach `cloudislands-<version>.zip`, optional addon bundle,
plugin jars, Core service runtime, developer kit, `checksums-sha256.txt`,
`cloudislands-sbom.cdx.json`, `provenance.json`, and generated `CHANGELOG.txt`.
The release gate is `./gradlew build distBundle distChecksums distSbom
distProvenance`; `distProvenance` records the commit, dirty state, artifact
paths, and SHA-256 digests. Pushing a matching `v*` tag runs the dedicated
Release workflow, repeats the full verification/security gates, and creates or
updates the GitHub Release with every distributable asset.
<!-- operator-release-docs:end -->

Quickstart, one Paper server:

- run one Paper server with the CloudIslands Paper plugin, one Core API, one
  SQL database, and one Core-internal Redis; Velocity and a separate lobby
  Paper server are not required
- start from `deploy/examples/single-paper/config-pack.yml`
- copy `deploy/examples/single-paper/.env.example` to `.env`, set
  `CLOUDISLANDS_STORAGE_PATH` to the Paper plugin's absolute local storage
  directory, create the three files under `secrets/`, and run
  `docker compose up -d --build --wait` from that example directory
- copy `deploy/examples/single-paper/config-v2/{runtime,integrations,security}.yml`
  into `plugins/CloudIslands/config-v2/`, alongside the other generated Config
  v2 files, copy the same Core/admin token files into
  `plugins/CloudIslands/secrets/`, then start Paper after
  `curl --fail http://127.0.0.1:8443/live` succeeds; `/ready` intentionally
  becomes healthy only after the Paper node starts sending heartbeats
- keep the Paper role as `ISLAND_NODE` so the same server runs commands, GUI,
  protection, island activation, saving, and restoration
- set `routing.direct-local-teleport: true` in Config v2 `integrations.yml`;
  ready Core route tickets are then consumed locally instead of using the
  BungeeCord/Velocity connect channel
- set `routing.local-fallback-world` to the exact lobby/spawn world name; local
  fallback never guesses from world load order
- direct-local routing disables the backend-only route-session, forwarding,
  and proxy-source login gates; keep the Paper server in online mode when it is
  exposed directly
- use `LOCAL_FILESYSTEM` storage for a small single-host deployment and back up
  `plugins/CloudIslands/islands-storage`, or keep S3-compatible storage when
  off-host durability is required
- Redis is disabled in the Paper plugin because it talks to Core over HTTP, but
  the single Core service still uses its private Redis container for durable
  job/event coordination; Redis is not published on the host

Quickstart, clustered single island node:

- run one Core API, one Redis, one SQL database, one object storage endpoint,
  one Velocity proxy, one lobby Paper server, and one island Paper node
- set each Paper `node.id` uniquely
- set `velocity-server-name` to the exact Velocity backend name for each node
- keep the Velocity forwarding secret identical between Velocity and Paper
- configure the Core API token or mTLS credentials before enabling production
  mode
- verify the island storage path points at durable object storage or a deliberate
  local test path

Quickstart, docker compose:

- create seven secret files for the database, Redis, storage access/secret keys,
  Core token, admin token, and Velocity forwarding secret; keep them outside the
  repository and make the forwarding secret at least 32 URL-safe characters
- export the corresponding `CLOUDISLANDS_*_FILE` variables, set
  `MINECRAFT_EULA=TRUE` only after accepting the Minecraft EULA, then run
  `docker compose -f deploy/compose/docker-compose.yml up -d --build --wait`
- the Compose project builds Core, Velocity 3.5, and Paper 1.21.11 images from
  this repository; set `CLOUDISLANDS_*_IMAGE` to use published/pinned images
- only Velocity port `25565` is public; Core defaults to loopback `8443`, while
  Paper, PostgreSQL, Redis, and MinIO remain unpublished
- verify `curl --fail http://127.0.0.1:8443/ready` reports `status: UP` and two
  route candidates before exposing Velocity
- run `./gradlew ciIntegrationSmoke` as the application integration gate

Production topology:

- Core API should run as at least two instances behind a trusted internal load
  balancer
- the supplied Compose topology routes every Velocity and Paper client through
  the `core-api` HAProxy service, which removes unhealthy Core instances using
  the unauthenticated `/ready` probe
- Redis, SQL, and object storage must be shared by all Core, Velocity, and Paper
  nodes
- Paper island nodes are cattle; island ownership lives in Core and object
  storage
- Velocity is the only public Minecraft entrypoint

Production security:

- never expose Core API, Redis, SQL, object storage, or Paper backends publicly
- require Core API token or mTLS for every Paper, Velocity, and admin client
- strip spoofable forwarding headers at the trusted proxy boundary
- keep `cloudislands.admin.*` permissions scoped to operators
- set `CI_ADMIN_PERMISSIONS` on Compose deployments or
  `core.adminPermissions` in Helm to the server-side permissions actually
  required; the secure default is `audit-read`, and island mutation commands
  additionally require `island-manage`
- permission names are case-insensitive and accept kebab-case; unknown
  `CI_ADMIN_PERMISSIONS` values fail Core startup with the invalid names listed
- `admin-api-enabled=true` requires a non-empty `CI_ADMIN_TOKEN`; deployments
  that intentionally omit an admin token must disable the admin API explicitly
- supplied Compose and Helm topologies enable admin routes on the internal Core
  service because Paper and Velocity call that URL; keep the Core bind private,
  and never enable this path on an Internet-exposed listener
- internal deployment templates default to an explicit full current-operator
  permission profile rather than `*`; this makes shipped admin commands usable
  while keeping every future permission opt-in

Backup and restore:

- back up SQL before releases and before SuperiorSkyblock2 imports
- back up object storage manifests and bundles together
- rehearse restore with `releaseClusterSmokeGate`
- verify snapshot manifest checksum and route handoff after restore
- back up the CoreProtect database separately when audit retention matters;
  CloudIslands never treats a CoreProtect rollback as an island-bundle restore
- WorldEdit and FAWE schematic or undo history is not snapshot payload; restored
  block state comes from the verified CloudIslands chunk bundle

Failure runbook:

- Core API failure: leave already loaded islands running, stop control-plane
  writes, and recover once Core health is restored
- Redis failure: expect degraded cache and stream behavior, then reconcile from
  SQL after Redis returns
- object storage failure: keep active islands local, queue failed saves, and
  block new activation or snapshot work until storage is healthy
- Paper node failure: drain or mark down the node, recover islands from latest
  verified bundles on another node

Migration from SuperiorSkyblock2:

- run scan, dry-run, backup, import approval, verify, and rollback planning in
  that order
- dry-run reports include JSON data and generated markdown text as runtime
  artifacts, not committed documentation
- incompatible owners, missing worlds, and unsupported legacy data block import
- rollback verifies both Core state and storage state

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

## Addon island commands

External addons can register collision-safe `/is` subcommands through
`api.addons().registerCommand(AddonIslandCommand)`. Registered aliases appear
in player tab completion and paginated command help, enforce the addon's
permission and argument range, and can return asynchronous messages without
blocking the Paper thread. CloudIslands rejects built-in and legacy SS2 alias
collisions and removes every owned command when the addon is disabled,
reloaded, unregistered, or the Paper plugin stops. The example addon registers
`/is example [status|events]` as the certification reference.

## SuperiorSkyblock2 command migration

The optional legacy alias adapter preserves player-command meaning for common
SS2 forms including `join`, `add`, `remove`, `bal`, `money`, `vault`,
`setbiome`, `tp`, `go`, `settp`, `setgo`, `show`, `showteam`, `online`, `tc`,
`leader`, `leadership`, and `expel`. Player aliases are not misclassified as
admin operations. `/is uncoop <player>` verifies that the target has the
temporary `TRUSTED` role before removal, so it can never remove a permanent
team member. `/is value [material]` resolves the held or named material against
typed Core block values and reports worth, level points, and placement limit.
Targeted SS2 forms preserve their optional player/island argument: `balance`,
`show`, and `team` resolve an island UUID, exact island name, or player's
primary island through Core. In migration mode, `warp <player|island> [warp]`
uses the same resolver instead of accepting UUIDs only or silently treating the
target as a local warp name.

## PlaceholderAPI

CloudIslands placeholders resolve the player's primary permanent island first,
then another permanent team island, and finally temporary co-op access. This
keeps owner, member, and co-op pages on the same Core-backed membership view.
Global ranking data is shared for the 15-second cache window instead of being
requested once per online player. Island, bank, member, and limit reads are
also coalesced per island, so a large team refreshing the same scoreboard does
not multiply Core requests by its online member count.

Alongside island identity, level, worth, rank, bank, public, and border values,
the expansion exposes `%cloudislands_role%`, `is_member`, `is_owner`, `is_coop`,
`team_size`, `coop_size`, `team_limit`, `coop_limit`, `team_list`, `coop_list`,
`locked`, `creation_time`, and `last_time_updated` (all with the
`%cloudislands_<key>%` form). `has_island` means permanent team membership;
`has_associated_island` also includes temporary co-op access.
SS2-style scoreboard aliases include `bank_format|int|raw`,
`worth_format|int|raw`, `level_format|int|raw`, `leader`, indexed `member_<n>`,
and `team_size_online`; formatted values use locale-stable `K`, `M`, `B`, `T`,
and `Q` suffixes.
Core-backed SS2 data aliases also include `biome`, `bans_count`, `bans_list`,
`home`, `home_x|y|z`, `world`, `warps`, `warps_limit`, and dynamic
`upgrade_<key>`, `permission_<permission>`, and `flag_<flag>`. Permission
results follow the runtime order of player override, role rule, then built-in
role defaults. Optional subsystem failures degrade only those values and do not
discard an otherwise healthy island snapshot.

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

Disabled means disabled:

- no command registration
- no GUI entry
- no listener
- no ticker
- no unnecessary writes
- existing island-scoped Satis database rows are preserved unless an operator
  explicitly removes island data

Satis state is scoped by CloudIslands island UUID.
If an island moves from server A to server B, Satis state follows the island.

### Satis operator commands

Use these before and after enabling the feature pack on a Paper island node:

- `/factory admin doctor`: first-line health summary. Check runtime, database,
  addon-state, dirty-save, route, lifecycle, config validation, blocked
  components, and the suggested operator action.
- `/factory admin database`: database backend view. Check active backend,
  fallback status, fallback risk, production safety, Core API cache backend, and
  local cache description.
- `/factory admin runtime`: runtime authority view. Check CloudIslands API
  availability, runtime owner fence, tick/write authority, dirty-save state, and
  Core API addon-state retry status.
- `/factory admin state`: raw addon-state snapshot for operators. Use it when
  comparing node-local diagnostics with Core API published state.

Feature-disable preservation:

- disabling `machines` stops placement, listeners, tickers, and machine GUI, but
  stored machine rows and machine inventories remain keyed by island UUID
- disabling `storage` stops storage commands, market, and contract flows that
  depend on storage, but virtual inventory rows remain in the Satis database
- disabling `resource-nodes` stops node generation and node scans, but stored
  node rows remain for re-enable or island relocation
- disabling `market`, `contracts`, `research`, or `maintenance` stops the
  matching gameplay surface without purging ledgers, unlocks, contract history,
  or maintenance debt
- disabling `addon-state` or `route-events` stops Core API publication for those
  surfaces; restore or migration should wait until publication is healthy again

### Legacy migration status

SuperiorSkyblock2 and legacy satismc import commands are no longer runtime
features in `cloudislands-satis`. Historical schema initialization remains in
`SatisSchemaService`, but legacy import, rollback, and migration command
surfaces are intentionally removed.

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

CloudIslands artifacts retain a Java 21 compilation and runtime baseline for
Paper 1.21.x. The Gradle 9.1 wrapper can run on Java 25, while toolchains select
Java 21 or Java 25 for the matching compile and server-launch task. Paper 26.1.x
and newer servers require Java 25.

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
| Paper `26.1.x` | `paper261Compile` | `paper261BootSmoke` | release-supported | stable Paper 26.1.2 API compile and boot verified on Java 25 |
| Paper `26.2.x` | `paper262Compile` | pending official Paper build | experimental compile-only | official alpha Paper API compile-verified on Java 25; stable boot build pending |
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
tests or generated release evidence. Paper 26.1.x has Java 25 compile and boot
coverage; compile-only 26.2.x adapter coverage is not reported as boot or
integration verification.

<!-- feature-parity:start -->
| Area | Status | Verified evidence | Limit |
|---|---|---|---|
| lifecycle/templates/homes/warps/visits | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies cross-Core create, job, route, session, consume, and player-ticket cache convergence; Paper tests verify target-island coordinates and bounded safe destinations | 26.1.2 is boot-verified; 26.2 stays compile-only until a stable Paper build is available |
| access/bans/membership/roles/permissions | IMPLEMENTED_VERIFIED | Core API and permission event replay are exercised in tests | third-party permission plugins are integration-status reported, not all boot-verified |
| flags/protection | IMPLEMENTED_VERIFIED | unit verified; Paper policy tests cover granular interactions, default-compatible natural flags, shard-safe player time/weather overrides, automation and growth boundaries, natural spread, material transitions, dependent block breaks, raids, mob targeting, and bounded asynchronous safe returns | runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests |
| ranking/level/worth/block values | IMPLEMENTED_VERIFIED | service-level verified | worth economics beyond configured value calculations are not release-certified |
| upgrades/size/border/biome | IMPLEMENTED_VERIFIED | verifyUpgradeEffectCoverage covers Core upgrade effects, atomic multi-price charging/refunds, rule-complete GUI views, and biome normalization; Paper tests cover world-border policy and chunk-batched biome painting | operator deployment acceptance is still recommended; CI verifies Core mutation plus cancellable, asynchronous Paper biome painting and border application policy |
| bank/economy/missions/challenges/generators/limits | IMPLEMENTED_VERIFIED | verifyMissionEventProgress covers block, farm, kill, fishing, crafting, enchanting, statistic, advancement, and item-consumption progress plus the bounded definition cache; reward-settlement tests cover failure reopening, repeatable reset, and durable warehouse item delivery, while upgrade CAS/refund, generator, and economy safety gates cover the remaining scope | brewing completion has no reliable Bukkit actor and is intentionally not guessed; operator live-server economy/provider acceptance is still recommended |
| chat/logs/reviews | IMPLEMENTED_VERIFIED | verifyReviewModerationCoverage plus Core audit/visitor route tests and LOWEST/HIGHEST private team-chat isolation cover current workflow | live multi-player chat moderation acceptance is deployment-specific outside unit CI |
| snapshots/rollback/migration/recovery | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies recovery restore with shared services | releaseClusterSmokeGate now includes database backup, object bundle, manifest checksum, restore, route, and audit evidence |
| Java API/events/addons | IMPLEMENTED_VERIFIED | apiCompatibilityCheck verifies release contract metadata and the public API signature baseline | external addon certification depends on testkit evidence supplied by the addon |
| integrations/localization/GUI | PARTIAL_VERIFIED | verifyIntegrationRuntimeSmoke verifies executable runtime services and keeps probe-only external adapters diagnostic | Vault and PlaceholderAPI runtime services are executable; external lifecycle and state-transfer operations remain diagnostic until real executors exist |
<!-- feature-parity:end -->

## Release

Current release: `v1.1.123`

Built for the CloudIslands 1.1.123 baseline.

Release notes for `v1.1.123`:

- truthful player withdrawal compensation: a failed Vault payout now reports a
  successful Core bank rollback only when the checked deposit result is accepted
- normal Core rejections such as a concurrently reached bank limit escalate to
  the existing rollback-failed operator path instead of hiding a balance mismatch
- successful rollback responses expose the authoritative post-refund balance,
  while rejected responses preserve the Core-reported balance for reconciliation
- regressions distinguish accepted, rejected, and exceptional rollback outcomes

Release notes carried forward from `v1.1.122`:

- truthful upgrade compensation: bank refunds now inspect the checked deposit
  result instead of treating every non-throwing call as success
- if another transaction fills the island bank between payment and refund, the
  purchase returns `_REFUND_FAILED` rather than incorrectly reporting `_REFUNDED`
- the existing balance remains authoritative and the failure code makes manual
  operator reconciliation visible instead of hiding lost compensation
- a concurrent-capacity regression covers the payment, intervening deposit,
  rejected refund, and final status sequence

Release notes carried forward from `v1.1.121`:

- concurrency-safe monotonic limits: upgrade effects now use an atomic
  repository-level `setAtLeast` operation instead of read-then-set
- PostgreSQL conditional upserts and MySQL/MariaDB `GREATEST` upserts preserve
  a higher concurrent mission or administrative grant
- lower requests leave both the stored limit and its authoritative updater
  unchanged
- Redis limit caches are invalidated after writes instead of replacing them
  with a potentially stale full-list snapshot
- a 1,000-writer concurrency regression proves convergence to the highest limit

Release notes carried forward from `v1.1.120`:

- monotonic upgrade effects: applying or recalculating a limit upgrade never
  reduces capacity already earned from missions or granted by administrators
- size, member, warp, home, border, biome, hopper, spawner, entity, redstone,
  bank, and crop-growth effects preserve the greater current value
- fixes low-level upgrade application replacing HOPPER 75 with 50 or BANK
  500,000 with 100,000 after an independent bonus
- regression coverage verifies both mission-style and administrative bonuses
  survive subsequent upgrade effect application

Release notes carried forward from `v1.1.119`:

- overflow-safe mission limit rewards: `LIMIT_INCREASE` checks the remaining
  `BIGINT` capacity before adding a reward
- a reward beyond capacity returns `LIMIT_REWARD_CAPACITY` with the current
  limit and requested amount for operator diagnostics
- rejected rewards preserve the complete previous island limit instead of
  wrapping negative or failing a database constraint
- normal and `Long.MAX_VALUE` boundary reward behavior is covered by the Core
  mission reward regression suite

Release notes carried forward from `v1.1.118`:

- database-safe island banking: every deposit path now respects the physical
  `DECIMAL(20,2)` maximum of `999999999999999999.99`
- configured bank limits above storage capacity are clamped, while the next
  cent beyond capacity returns `BANK_LIMIT` without changing the balance
- sub-cent deposits and withdrawals return `INVALID_AMOUNT` instead of being
  rounded differently by in-memory, PostgreSQL, and MySQL/MariaDB backends
- mission bank rewards use the same checked deposit result and no longer report
  success when the durable balance cannot accept the reward

Release notes carried forward from `v1.1.117`:

- overflow-safe island warehouse deposits: item totals can reach the exact
  `BIGINT` maximum but never wrap negative or fail as an ambiguous DB error
- PostgreSQL and MySQL/MariaDB deposits now create, lock, validate, update, and
  read the warehouse row in one transaction
- deposits beyond capacity return the explicit `WAREHOUSE_LIMIT` result while
  preserving the complete previous balance
- in-memory and JDBC repositories share the same capacity policy, with boundary
  and unchanged-balance regression coverage

Release notes carried forward from `v1.1.116`:

- durable job payload integrity: queued activation, save, snapshot, restore,
  migration, and recovery metadata now uses the shared bounded JSON codec
- object-storage paths, administrative reasons, and metadata containing commas,
  colons, quotes, backslashes, newlines, Korean text, or emoji round-trip intact
- null job metadata normalizes predictably and malformed legacy payloads fail
  closed instead of producing partially shifted job arguments
- PostgreSQL and MySQL/MariaDB job workers now decode identical payload maps

Release notes carried forward from `v1.1.115`:

- lossless island activity logs: structured payloads now use the shared bounded
  JSON codec instead of splitting stored JSON on commas
- reasons, messages, names, and metadata containing commas, colons, quotes,
  backslashes, control characters, Korean text, or emoji round-trip unchanged
- null payload values normalize predictably and invalid legacy payloads fail
  closed to an empty map without breaking the complete log history response
- PostgreSQL and MySQL/MariaDB continue using their native JSON storage paths
  with identical decoded payload behavior

Release notes carried forward from `v1.1.114`:

- complete MySQL/MariaDB actor UUID reads: `created_by`, `updated_by`, and
  `moderated_by` `CHAR(36)` values now convert to Java UUIDs like PostgreSQL
- fixes runtime `ClassCastException` risks while loading snapshots, homes,
  warps, biomes, and island limits
- nullable UUIDs and both text/binary driver representations are preserved
  safely, while non-UUID fields such as job `locked_by` remain strings
- schema-wide regression coverage checks every MySQL `CHAR(36)` UUID column
  against the dialect conversion policy

Release notes carried forward from `v1.1.113`:

- overflow-safe ranking math: extreme block counts and configured level points
  now saturate at `BIGINT` capacity instead of wrapping into invalid values
- database-safe island worth: calculated worth is normalized to `DECIMAL(20,2)`
  precision and capped before snapshot persistence, preventing endless dirty
  retries caused by numeric overflow
- cache/event consistency: per-block worth is normalized before calculation so
  in-memory, Redis, event payloads, PostgreSQL, and MySQL snapshots agree
- MySQL/MariaDB ranking reads now convert `CHAR(36)` island identifiers instead
  of assuming the JDBC driver returns PostgreSQL-style UUID objects

Release notes carried forward from `v1.1.112`:

- empty-island ranking correctness: clearing every counted block now queues a
  durable recalculation and resets stale level/worth snapshots to zero
- island-scoped dirty queue: ranking work no longer depends on a surviving
  `island_block_counts` row
- concurrency-safe drain: queue rows are locked and deleted in one transaction,
  so a concurrent dirty mark cannot be cleared accidentally
- PostgreSQL and MySQL/MariaDB upgrades backfill pending legacy dirty rows into
  the new queue; block count writes enqueue ranking work atomically

Release notes carried forward from `v1.1.111`:

- usable default operator profile: supplied internal deployments explicitly
  grant every one of the 23 currently supported admin permissions
- no wildcard privilege drift: the profile deliberately avoids `*`, so future
  permissions remain denied until operators or a reviewed release add them
- fixes shipped `/ciadmin` mutation commands still returning 403 under the old
  audit-only deployment default
- enum-completeness, no-wildcard, deployment wiring, Compose syntax, and Core
  security tests lock the profile to the current permission contract

Release notes carried forward from `v1.1.110`:

- reachable in-game admin commands: supplied Compose and Helm topologies now
  enable admin routes on the internal Core listener used by Paper and Velocity
- fixes valid-token `/ciadmin` calls being permanently rejected because the
  only enabled admin listener was container-local loopback
- security boundary preserved: Core remains host-loopback/ClusterIP internal,
  with admin token, server permissions, request guards, and rate limits intact
- production HA, single-paper, Helm values, operator guidance, Compose syntax,
  and deployment regression coverage now agree on the listener contract

Release notes carried forward from `v1.1.109`:

- fail-fast admin authentication: `admin-api-enabled=true` now requires a
  non-empty `CI_ADMIN_TOKEN` before Core starts
- no permanently inaccessible admin surface caused by a missing token while
  the API reports itself enabled
- actionable startup error tells operators to configure the token or explicitly
  disable the admin API
- intentional no-admin deployments remain supported when
  `CI_ADMIN_API_ENABLED=false`, with config-doctor and regression coverage

Release notes carried forward from `v1.1.108`:

- fail-fast admin permission configuration: unknown `CI_ADMIN_PERMISSIONS`
  names now stop Core startup instead of silently producing later 403 failures
- actionable diagnostics list every invalid permission token in the startup
  exception while preserving accepted kebab-case and enum-style names
- wildcard and secure empty/default policies remain supported
- config-doctor startup-boundary metadata, application comments, README
  guidance, and regression tests now include permission validation

Release notes carried forward from `v1.1.107`:

- deployable server-side admin permissions: Compose HA, single-paper, and Helm
  now forward the configured permission list into the Core runtime
- conservative upgrade default: `audit-read` remains the default instead of
  silently granting wildcard mutation access
- operator-controlled enablement through `CI_ADMIN_PERMISSIONS` for Compose or
  `core.adminPermissions` for Helm, including the new `island-manage` scope
- deployment regression coverage and Compose config validation prevent future
  charts or examples from dropping the permission wiring

Release notes carried forward from `v1.1.106`:

- admin-route permission coverage gate: Core tests now extract every statically
  registered admin route and require an explicit server guard permission
- future-proof fail-close behavior: adding an admin API without updating its
  permission mapping fails `:cloudislands-core-service:check`
- dynamic island and node routes remain covered by their dedicated
  least-privilege suffix tests
- current route audit confirms every registered static admin endpoint is usable
  under its configured permission instead of being permanently denied

Release notes carried forward from `v1.1.105`:

- usable registered admin APIs: previously unmapped island management,
  ranking, generator, player-disband, and migration routes no longer fail with
  permanent 403 responses under valid server-side permissions
- new least-privilege `ISLAND_MANAGE` permission covers member, permission,
  bank, generator, limit, mission, upgrade, biome, flag, home, and warp actions
- correct delete classification: home and warp deletion no longer require the
  destructive whole-island `ISLAND_DELETE` permission
- Core client permission metadata now matches the server guard, with regression
  coverage for positive access, audit-only denial, and unknown-route fail-close

Release notes carried forward from `v1.1.104`:

- authoritative empty-list caching: a versioned Redis sentinel distinguishes a
  valid empty result from a cache miss or corrupt payload
- no empty-result DB stampede for new islands without rankings, missions,
  snapshots, logs, limits, upgrades, or templates
- sentinel integrity remains fail-safe: extra or malformed rows force an
  authoritative reload instead of being accepted as empty
- regression coverage verifies empty cache hits and contaminated-sentinel
  rejection

Release notes carried forward from `v1.1.103`:

- all-or-authoritative list caches: any malformed row now invalidates the whole
  Redis payload so Core reloads the complete durable list
- no partial-data exposure: one corrupt ranking, mission, snapshot, log, limit,
  upgrade, or template row can no longer silently hide only that record
- shared completeness guard compares encoded non-empty rows with successfully
  parsed rows before accepting a cache hit
- regression coverage verifies valid, fully corrupt, and partially corrupt
  payload behavior

Release notes carried forward from `v1.1.102`:

- self-healing list caches: fully corrupt non-empty Redis payloads now become
  cache misses so Core reloads authoritative data instead of returning `[]`
- broad production coverage for ranking, missions, snapshots, island logs,
  limits, upgrades, and templates
- corrupt payloads fall back to authoritative storage instead of exposing an
  incomplete list
- regression coverage verifies corrupt template payload fallback and valid
  cache-hit preservation

Release notes carried forward from `v1.1.101`:

- lossless template caching: `createdAt` and `updatedAt` now survive Redis
  serialization instead of becoming Unix epoch values on cache hits
- consistent template APIs: database reads and cached reads now expose the same
  lifecycle timestamps to admin tools and creation-menu consumers
- rolling-upgrade compatibility: existing 28-field Redis rows remain readable
  while new 30-field rows preserve both timestamps
- regression coverage verifies current-format round trips and legacy cache-row
  fallback behavior

Release notes carried forward from `v1.1.100`:

- reliable template administration with Redis: delete and reorder operations
  now reach the durable template repository through the production cache layer
- immediate template-list refresh: successful mutations repopulate the shared
  cache so creation menus and admin views cannot keep stale entries or ordering
- fail-closed template contract: every repository implementation must provide
  delete and reorder behavior instead of inheriting silent `false` no-ops
- regression coverage verifies both mutations with the cache wrapper enabled
  even while Redis is unavailable

Release notes carried forward from `v1.1.99`:

- reliable ranking exclusions with Redis: admin ignore and unignore operations
  now reach the durable ranking repository through the production cache wrapper
- immediate ranking refresh: changing an exclusion bumps the shared ranking
  cache version so previously cached level and worth boards cannot stay stale
- fail-closed repository contract: ignore reads and writes are mandatory for
  every ranking repository implementation instead of silently becoming no-ops
- regression coverage verifies ignore and unignore behavior with the Redis
  wrapper enabled even when Redis itself is temporarily unavailable

Release notes carried forward from `v1.1.98`:

- exact public-island page ordering: eligible islands are globally ordered by
  level descending, name, and stable ID before offset and limit are applied
- no page-local ranking drift: high-level islands can no longer appear on a
  later page merely because the database first divided rows by creation time
- visibility safety is preserved: public access, unlocked state, and active
  deletion state remain part of the database filter before pagination
- deterministic ties: island ID provides a stable final ordering when level and
  name are identical

Release notes carried forward from `v1.1.97`:

- exact public-warp pages: island visibility, lock state, warp visibility, and
  `PUBLIC_WARPS` are filtered before offset and limit are applied
- no hidden-row starvation: private or locked islands at the front of the
  newest-first ordering can no longer produce empty pages while valid rows exist
- JDBC query consolidation: discoverability is resolved with joined islands and
  flags in one query instead of per-warp N+1 metadata reads
- stable filtering: category and text search participate in the same filtered
  ordering and pagination contract
- in-memory parity and regression coverage verify first and subsequent pages
  when a newer hidden warp precedes older visible warps

Release notes carried forward from `v1.1.96`:

- localized proxy safety guidance: Paper-only mutation refusals now use the
  configured Velocity message catalog instead of hard-coded Korean text
- complete Korean and English coverage for backend-required commands, home and
  warp location writes, warehouse inventory changes, and bank settlement
- configurable operations: operators can override every new safety message in
  `config-v2/messages/ko_kr.yml` or `en_us.yml`
- consistent fallback behavior: built-in defaults retain safe guidance even
  when deployed configuration is missing a newly introduced key
- audit confirmation: paid templates remain Paper-gated while Core-authoritative
  bank/warehouse upgrade purchases remain safe on either network surface

Release notes carried forward from `v1.1.95`:

- inventory-authoritative warehouse mutations: deposits and withdrawals run
  only on Paper where the player's actual inventory can be changed atomically
- no proxy item duplication: Velocity can no longer credit Core warehouse
  stock without first removing the deposited items
- no proxy item loss: Velocity can no longer debit warehouse stock without
  delivering the withdrawn items to a real inventory
- read/write boundary: warehouse listing remains available on Velocity while
  mutation commands are forwarded to Paper or safely refused
- corrected regression contract replaces the old requirement that proxy-side
  warehouse mutations directly execute

Release notes carried forward from `v1.1.94`:

- no synthetic proxy locations: Velocity no longer persists homes or warps at
  a hard-coded shard coordinate when it cannot observe a Bukkit location
- Paper-local enforcement: location-dependent commands are forwarded only when
  the player has a current backend server
- safe disconnected behavior: forwarded commands are explicitly denied with an
  actionable message instead of falling through to a proxy mutation handler
- defense in depth: direct Velocity set-home/set-warp handlers also refuse to
  mutate Core state without an authoritative Paper location
- regression coverage ensures neither Velocity action class can call the typed
  home/warp setters or construct the old synthetic coordinate

Release notes carried forward from `v1.1.93`:

- operator home recovery: `/ciadmin island delhome <island> <home>` removes a
  stale or invalid home without requiring an online owner or player permission
- typed admin endpoint: `/v1/admin/islands/homes/delete` is exposed through the
  compatible Core home/warp command client
- precise recovery failures: missing islands return `ISLAND_NOT_FOUND` while a
  missing named home returns `HOME_NOT_FOUND`
- accountable operations: successful operator removal records
  `ISLAND_HOME_ADMIN_DELETE` and emits `HOME_ADMIN_DELETE` cache evidence
- admin completion and help now advertise both `delhome` and `deletehome`

Release notes carried forward from `v1.1.92`:

- complete home lifecycle: players can now delete named or default homes and
  immediately reclaim the released home slot
- end-to-end typed contract: `/v1/islands/homes/delete`, Core client, public
  Java API, Paper API, and Velocity execution share `HOME_DELETED`
- Paper and proxy commands: `delhome`, `deletehome`, `home-delete`, and
  `홈삭제` work with optional names and the existing set-home permission
- authoritative deletion evidence: successful removal writes one audit entry,
  one island log, and one `ISLAND_HOME_CHANGED` event with `HOME_DELETE`
- compatibility-safe API growth: default interface methods preserve existing
  third-party command-service and Core-client implementations

Release notes carried forward from `v1.1.91`:

- idempotent home and warp sets: identical location/configuration requests
  return 200 without rewriting creator metadata or timestamps
- authoritative resource comparison: JDBC locks the named home or warp row and
  compares its complete persisted state inside the island transaction
- quiet replay behavior: unchanged requests skip audit, island-log,
  `ISLAND_HOME_CHANGED`, `ISLAND_WARP_CREATED`, and `ISLAND_WARP_CHANGED`
- single limit authority: warp routes no longer perform race-prone list/count
  prechecks before the repository's locked limit decision
- cache efficiency: cached home and warp collections refresh only after a
  created or updated result

Release notes carried forward from `v1.1.90`:

- idempotent warp visibility: repeated public/private requests succeed without
  duplicate audit, island-log, or `ISLAND_WARP_CHANGED` events
- authoritative JDBC outcome: the current warp row is locked before returning
  `APPLIED`, `UNCHANGED`, or `WARP_NOT_FOUND`
- truthful HTTP semantics: real visibility transitions return 202 while
  already-satisfied requests return 200 with the existing typed client code
- cache parity: the caching repository refreshes warp state only after an
  applied visibility transition

Release notes carried forward from `v1.1.89`:

- idempotent biome mutation: repeated biome choices return `BIOME_UNCHANGED`
  without duplicate audit, island-log, or Paper repaint events
- transactional comparison: JDBC locks the active island and biome row before
  returning `APPLIED`, `UNCHANGED`, or `ISLAND_NOT_FOUND`
- authoritative event delivery: `ISLAND_BIOME_CHANGED` is published only for
  the request that actually changes persistent state
- in-memory parity: development and test storage preserves the first updater
  and timestamp when a later request selects the same biome

Release notes carried forward from `v1.1.88`:

- idempotent protection flags: repeated values return `ISLAND_FLAG_UNCHANGED`
  without duplicate protection-cache invalidation events
- truthful empty resets: resetting an already empty flag set returns
  `ISLAND_FLAGS_UNCHANGED` without audit or island-log noise
- transactional comparison: JDBC locks the active island and current flag row
  before returning `APPLIED`, `UNCHANGED`, or `ISLAND_NOT_FOUND`
- upgrade replay safety: repeated flag-grant effects retain upgrade evidence but
  publish `ISLAND_FLAG_CHANGED` only when the stored value changes

Release notes carried forward from `v1.1.87`:

- authoritative rename outcomes: player and administrator routes distinguish
  `APPLIED`, `UNCHANGED`, `ISLAND_NAME_TAKEN`, and `ISLAND_NOT_FOUND`
- idempotent naming: reapplying the exact name returns
  `ISLAND_NAME_UNCHANGED` without duplicate rename events or audit entries
- race-safe uniqueness: the active-name case-insensitive unique index resolves
  simultaneous cross-island rename attempts, including MySQL and PostgreSQL
- precise operator feedback: duplicate names no longer collapse into generic
  `ISLAND_RENAME_DENIED` responses

Release notes carried forward from `v1.1.86`:

- idempotent access settings: repeated lock and public-access writes return
  `ISLAND_LOCK_UNCHANGED` or `ISLAND_ACCESS_UNCHANGED`
- clean observability: unchanged access requests no longer duplicate audit
  entries, island logs, or `ISLAND_ACCESS_CHANGED` events
- serialized comparisons: JDBC reads the active island row `FOR UPDATE` before
  returning `APPLIED`, `UNCHANGED`, or `ISLAND_NOT_FOUND`
- projection repair preserved: unchanged public-access requests still refresh
  the metadata projection without claiming an authoritative state change

Release notes carried forward from `v1.1.85`:

- idempotent role writes: reapplying the same permanent role returns
  `MEMBER_UNCHANGED` without role-change events, audit entries, or row rewrites
- authoritative JDBC outcomes: the island-locked transaction compares stored
  role and temporary-expiry state before returning `APPLIED` or `UNCHANGED`
- in-memory production parity: synchronized limited upserts now enforce both
  total team capacity and per-role capacity under concurrent access
- renewal safety: temporary trust renewals remain real updates while expired
  memberships are never misclassified as unchanged

Release notes carried forward from `v1.1.84`:

- truthful invite acceptance events: successful joins publish explicit
  `ACCEPTED` state while non-mutating failures publish no change event
- stale-invite observability: an invite invalidated because the target already
  joined is logged and published as `EXPIRED` with reason `ALREADY_MEMBER`
- clean audit semantics: capacity, missing-island, and unavailable-invite
  failures are no longer recorded as invite acceptance actions
- consumer-ready payloads: invite events now carry explicit state and boolean
  outcome fields for Paper/addon event subscribers

Release notes carried forward from `v1.1.83`:

- authoritative invite declines: repositories distinguish `APPLIED`,
  `EXPIRED`, and `INVITE_UNAVAILABLE` instead of collapsing every rejection
- precise player feedback: attempts against expired invitations return
  `INVITE_EXPIRED` while missing or already handled invitations remain
  `INVITE_UNAVAILABLE`
- ghost-event prevention: unavailable and expired decline requests no longer
  publish a false `ISLAND_INVITE_CHANGED` event or successful decline audit
- repository parity: JDBC, cached, and synchronized in-memory implementations
  expose the same compatibility-preserving decline-result contract

Release notes carried forward from `v1.1.82`:

- truthful visitor pardons: removing a nonexistent or expired active ban now
  returns `BAN_NOT_FOUND` instead of reporting a successful state change
- ghost-event prevention: rejected pardons no longer write audit/island logs or
  publish `ISLAND_VISITOR_BAN_CHANGED`
- transactional outcomes: JDBC checks the affected active-ban row under the
  same island lock shared with visitor-ban creation
- repository parity: cached and synchronized in-memory implementations expose
  the same compatibility-preserving pardon-result contract

Release notes carried forward from `v1.1.81`:

- stale-invite role safety: accepting an invite after the target became a team
  member returns `ALREADY_MEMBER` instead of overwriting their current role
- promotion preservation: `MODERATOR`, `CO_OWNER`, and custom team roles cannot
  be silently demoted to `MEMBER` by an older pending invite
- transactional cleanup: JDBC expires the obsolete invite under the same island
  lock used to recheck authoritative ownership and membership
- repository parity: synchronized in-memory acceptance now returns precise
  `MEMBER_LIMIT`, `ALREADY_MEMBER`, and `INVITE_UNAVAILABLE` outcomes

Release notes carried forward from `v1.1.80`:

- truthful member removals: player leave and administrator kick requests now
  return `MEMBER_NOT_FOUND` when no active membership was actually removed
- ghost-event prevention: rejected removals no longer publish
  `ISLAND_MEMBER_LEFT` or `ISLAND_MEMBER_CHANGED`, nor create misleading audit
  and island-log entries
- race-safe persistence: JDBC deletion reports its affected-row result inside
  the primary-island cleanup transaction and excludes expired temporary trust
  rows from successful member removals
- repository parity: JDBC, cached, and synchronized in-memory repositories
  expose the same compatibility-preserving removal-result contract

Release notes carried forward from `v1.1.79`:

- authoritative home outcomes: the locked home upsert returns `CREATED` or
  `UPDATED` after checking the stored named row
- operation-aware observability: audit entries, island logs, and
  `ISLAND_HOME_CHANGED` payloads include `HOME_CREATE` or `HOME_UPDATE`
- concurrent accuracy: simultaneous requests for the same home name are
  classified from serialized database state rather than a route precheck
- compatibility preserved: existing home-change event consumers safely ignore
  the additive operation field

Release notes carried forward from `v1.1.78`:

- authoritative warp outcomes: the locked upsert transaction now returns
  `CREATED` or `UPDATED` after checking the stored named row
- duplicate-event prevention: simultaneous requests for the same new warp emit
  `WARP_CREATED` only for the transaction that actually inserted it
- accurate change operations: the later serialized request publishes
  `WARP_UPDATE` instead of relying on a stale route-level existence read
- repository parity: JDBC and synchronized in-memory implementations expose the
  same creation-state result while retaining fallback compatibility

Release notes carried forward from `v1.1.77`:

- locale-stable resource keys: home and warp names use `Locale.ROOT` instead of
  the host JVM locale for every create, lookup, update, access, and delete path
- whitespace normalization: leading and trailing spaces are removed before
  validation and persistence, preventing visually duplicate resource names
- Turkish-locale coverage: regression tests prove uppercase ASCII resource
  names normalize identically under locale-sensitive host settings
- repository parity: JDBC and in-memory implementations apply the same
  defensive normalization even when called outside HTTP routes

Release notes carried forward from `v1.1.76`:

- truthful island access mutations: lock and public-access changes now use the
  database affected-row result instead of assuming every update succeeded
- deleted-island protection: a deletion racing with a settings request returns
  `ISLAND_NOT_FOUND` before audit logs or access-change events are emitted
- active-row lock updates: JDBC lock changes exclude soft-deleted islands with
  `deleted_at IS NULL`
- repository parity: JDBC, cached, and in-memory island repositories expose the
  same compatibility-preserving boolean result contract

Release notes carried forward from `v1.1.75`:

- truthful warp mutations: delete and public-access updates now return whether
  a stored row was actually changed instead of always reporting success
- missing-warp responses: player and admin operations return `WARP_NOT_FOUND`
  before writing audit entries or publishing change events
- race-safe admin deletion: the final DELETE affected-row result closes the gap
  between the admin existence precheck and the mutation
- repository parity: JDBC, cached, and in-memory implementations preserve the
  same boolean mutation-result contract

Release notes carried forward from `v1.1.74`:

- database-safe resource names: home and warp creation rejects empty,
  whitespace-only, control-character, and over-32-character names before SQL
- actionable client errors: invalid inputs return `INVALID_HOME_NAME` or
  `INVALID_WARP_NAME` with player-safe Korean messages instead of a server 500
- localized home capacity: `HOME_LIMIT` now has an explicit Korean player
  message and domain error category alongside `WARP_LIMIT`
- international names preserved: valid visible Unicode names remain supported
  within the database length boundary

Release notes carried forward from `v1.1.73`:

- functional home upgrades: the `HOMES` limit produced by `HOME_LIMIT`
  upgrades is now enforced by the home creation endpoint
- atomic last-slot enforcement: home and warp creation lock the island row and
  count named resources inside the same database transaction
- update-safe limits: changing an existing named home or warp remains allowed
  at capacity, while only a genuinely new name consumes a slot
- concurrent overbooking protection: simultaneous distinct home/warp creates
  cannot both consume the final configured slot
- repository parity: JDBC, cached, and in-memory implementations return
  `HOME_LIMIT`, `WARP_LIMIT`, or `ISLAND_NOT_FOUND` consistently

Release notes carried forward from `v1.1.72`:

- expiry-correct ban cache: cached visitor bans are revalidated against their
  `expiresAt` value on every Redis read instead of lasting until cache TTL
- clean cache writes: already expired bans are excluded before serializing the
  island ban list, reducing stale cache payloads
- boundary-tested semantics: permanent bans remain active, future expiries are
  active, and bans expiring at or before the current instant are inactive

Release notes carried forward from `v1.1.71`:

- deterministic ban/pardon ordering: both operations now serialize through the
  same island-row lock before mutating the visitor ban record
- no missed in-flight ban: a later pardon waits for an earlier ban commit and
  then removes it instead of observing zero rows and leaving the ban active
- runtime parity: in-memory ban and pardon operations use the same synchronized
  boundary as the transactional JDBC implementation

Release notes carried forward from `v1.1.70`:

- atomic visitor-ban eligibility: Core locks the island and target membership,
  then rechecks owner and every member role before writing a ban
- join-race protection: a player who becomes a member while a ban request is in
  flight cannot be banned or removed by the stale route precheck
- no split membership mutation: visitor bans no longer perform a second,
  separately committed member deletion after writing the ban
- repository parity: JDBC, cached, and in-memory implementations expose the
  same authoritative `APPLIED`, `VISITOR_BAN_DENIED`, and not-found outcomes

Release notes carried forward from `v1.1.69`:

- one-active-invite parity: the in-memory runtime now expires an earlier invite
  when the same island reinvites the same target, matching JDBC behavior
- duplicate-free local UX: pending invite lists expose only the newest active
  invite instead of accumulating stale alternatives
- concurrency-safe replacement: synchronized in-memory creation keeps the
  one-pending-invite invariant under simultaneous requests

Release notes carried forward from `v1.1.68`:

- expiry-correct decline behavior: an invite past its deadline can no longer be
  declined successfully merely because its stored state was still `PENDING`
- durable stale-state cleanup: JDBC decline locks the invite, commits its state
  as `EXPIRED`, and returns unavailable without emitting a false decline event
- runtime parity: the synchronized in-memory repository performs the same
  expiry transition for tests and local deployments

Release notes carried forward from `v1.1.67`:

- authoritative invite outcomes: acceptance now returns the result determined
  inside the locked database transaction instead of reconstructing it afterward
- actionable capacity errors: a full island reports `MEMBER_LIMIT` even when
  another request consumes the last slot during acceptance
- deleted-island clarity: an invite whose island disappeared reports
  `ISLAND_NOT_FOUND`; expired, consumed, and wrong-target invites remain
  `INVITE_UNAVAILABLE`
- compatibility preserved: existing boolean repository methods delegate to the
  structured result path and successful cached membership refresh is unchanged

Release notes carried forward from `v1.1.66`:

- transactional invite replacement: expiring an earlier pending invite and
  inserting its replacement now commit as one database operation
- concurrency-safe reinvites: invite creation serializes on the island row so
  simultaneous requests cannot collide with the one-pending-invite constraint
- rollback-safe failure behavior: an insertion failure no longer leaves the
  previous usable invite expired by a separately committed statement

Release notes carried forward from `v1.1.65`:

- concurrency-safe role caps: member edits, admin role changes, and temporary
  trust now serialize on the island row before rechecking the target role limit
- no trusted-slot overbooking: simultaneous co-op requests cannot both consume
  the final `TRUSTED` slot
- renewal-safe temporary trust: extending an existing temporary trust entry does
  not consume another role slot even when the role is full
- atomic legacy admin transitions: promotions into the team enforce both the
  global member cap and target role cap in the membership transaction
- fail-safe responses: rejected role changes return `ROLE_LIMIT`,
  `MEMBER_LIMIT`, or `ISLAND_NOT_FOUND` without partially updating membership

Release notes carried forward from `v1.1.64`:

- concurrency-safe member caps: direct team joins serialize on the island row
  before rechecking limits
- no last-slot overbooking: simultaneous owner/admin additions cannot both
  consume the final `MEMBERS` slot
- atomic role caps: the target role count is rechecked inside the same
  transaction, returning `ROLE_LIMIT` when full
- precise transition counting: moves between existing team roles do not consume
  another global member slot
- fail-safe responses: the transaction returns `MEMBER_LIMIT`, `ROLE_LIMIT`, or
  `ISLAND_NOT_FOUND` without partially writing membership or primary state

Release notes carried forward from `v1.1.63`:

- direct-add home readiness: owner role assignment and admin member add now
  initialize an empty primary island as part of the member transaction
- team-transition precision: initialization runs only when a player crosses
  from non-team status into a MEMBER-family role
- co-op preference safety: temporary `TRUSTED` visitors never have their
  selected island changed
- existing selection preservation: adding a player to another team does not
  overwrite an already selected island
- end-to-end lifecycle proof: admin add immediately selects the island and the
  existing atomic kick path clears it without test-only profile setup

Release notes carried forward from `v1.1.62`:

- atomic invite acceptance: invite state, member upsert, profile creation, and
  initial primary-island selection now commit in one JDBC transaction
- consumed-invite safety: Core cannot mark an invite accepted and stop before
  the invited player becomes an actual member
- immediate home consistency: a first-time member receives the joined island
  as `/is home` selection in the authoritative transaction
- existing preference preservation: accepting another invite never overwrites
  a player who already selected an island
- PostgreSQL and MySQL parity: profile creation uses database-specific
  conflict-safe insert syntax before the conditional primary update

Release notes carried forward from `v1.1.61`:

- atomic member removal: membership deletion and selected-island cleanup now
  commit in one JDBC transaction for both player and admin kick routes
- stale-profile repair: a non-owner whose membership row was already missing
  still loses a stale primary-island reference during removal
- owner safety: the conditional profile update explicitly excludes the
  authoritative island owner even if a route or imported projection is wrong
- unrelated selection safety: removing a player from one island does not clear
  a different island they currently selected
- cache reconciliation: the existing profile operation refreshes Redis after
  the authoritative database transaction without reopening the crash window

Release notes carried forward from `v1.1.60`:

- atomic ownership transfer: island owner UUID, former-owner `CO_OWNER`,
  new-owner `OWNER`, and new-owner primary island now commit together
- crash-window closure: Core cannot commit ownership and then stop before the
  new owner receives the island as their selected `/is home` destination
- migrated-data repair: the former owner role uses an upsert, preserving them
  as co-owner even when an older import omitted its membership projection
- dual-database parity: PostgreSQL and MySQL/MariaDB receive equivalent owner
  and co-owner upsert semantics
- cache-compatible completion: the route-level profile update remains as a
  harmless cache refresh after the authoritative JDBC transaction

Release notes carried forward from `v1.1.59`:

- settled-price binding: Paper sends the exact amount successfully withdrawn
  from Vault with the managed Core creation request
- authoritative price comparison: Core compares the settled amount against the
  current template price numerically, including scale-equivalent values such as
  `250` and `250.00`
- admin-race compensation: a price change between catalog display and Core
  create returns `PAID_TEMPLATE_PRICE_CHANGED`, triggering the existing refund
- missing-quote fail-closed behavior: a managed flag without a positive settled
  price no longer authorizes paid creation
- player guidance: Korean and English messages explain that a changed price was
  refunded and the latest catalog price should be reviewed

Release notes carried forward from `v1.1.58`:

- Core-enforced paid creation: a paid template now requires an explicit
  economy-settlement-managed create request at the authoritative workflow
- safe default API behavior: existing Java API and HTTP clients send an
  unmanaged request and cannot accidentally create a paid template for free
- Paper settlement handoff: only the path after successful Vault withdrawal
  marks the Core request as economy-settlement-managed
- race closure: if an administrator changes a template from free to paid after
  a proxy or addon reads it, Core rechecks the current cost and rejects create
- fail-closed catalog parsing: malformed positive-price metadata also requires
  the protected settlement path
- localized guidance: Paper explains that paid templates must use the
  payment-protected creation menu

Release notes carried forward from `v1.1.57`:

- no free-island ambiguity: Core persists the PREPARING route ticket before
  publishing the create job, so a ticket-store failure cannot leave a running
  creation after Paper refunds the player
- failed-ticket settlement: job queue rejection moves the prepared ticket to
  `FAILED` and records `ERROR_CREATING` with an operator-visible reason
- automatic recovery: retrying the original template resumes the same failed
  island ID instead of returning permanent `ALREADY_HAS_ISLAND`
- price-policy preservation: an `ERROR_CREATING` island cannot be retried with
  a different or free template
- localized recovery guidance: Paper and Velocity distinguish unavailable
  route tickets from failed-create template mismatches
- Paper 26 boot evidence: CI recognizes both the legacy `Done (` marker and
  Paper 26's `Done preparing level` marker while still requiring CloudIslands
  plugin activation, eliminating a four-minute false failure
- stable multi-Core load evidence: the replay probe refreshes the active Paper
  node heartbeat while waiting for Redis delivery and keeps the remote-event
  assertion strict across slower CI runners

Release notes carried forward from `v1.1.56`:

- proxy payment bypass closure: Velocity now loads the authoritative template
  before issuing a Core island-create mutation
- permission parity: template `requiredPermission` is enforced consistently on
  both Paper and Velocity command paths
- paid-template safety: Velocity refuses paid templates and directs players to
  the Paper path where Vault withdrawal and compensation are available
- fail-closed pricing: malformed creation costs are treated as paid instead of
  silently becoming free
- localized operator experience: Korean and English proxy messages explain the
  permission denial and payment-safe creation route

Release notes carried forward from `v1.1.55`:

- duplicate-charge protection: a per-player in-flight guard covers template
  lookup, economy withdrawal, Core creation, and compensation settlement
- explicit compensation outcome: Core transport failures now return a clear
  refunded result after a successful economy rollback
- refund escalation: a failed rollback is preserved as
  `ECONOMY_REFUND_FAILED` with localized guidance to stop retrying and inspect
  the transaction log
- lock lifecycle proof: unit coverage verifies duplicate rejection and lock
  reuse only after the prior settlement completes
- version coverage: the hardened paid-create flow compiles against Paper 1.21,
  26.1, and 26.2 adapters

Release notes carried forward from `v1.1.54`:

- private team-chat isolation: team-mode messages are cancelled at Paper's
  `LOWEST` priority before ordinary chat integrations process them
- no residual audience: the global viewer set is cleared and the renderer is
  replaced with an empty fail-closed renderer before routing to Core
- uncancel protection: isolation is reasserted at `HIGHEST`, preventing an
  intermediate plugin from restoring global delivery
- moderation compatibility: a message already cancelled before CloudIslands
  runs remains blocked and is not forwarded into the private TEAM channel
- no spy crossover: team-mode messages return before the global admin-spy path;
  authorized team delivery remains exclusively in Core's `TEAM` channel
- scheduler safety: the async chat handler still returns to the Paper scheduler
  before reading player location or invoking the Core communication client
- version coverage: the hardened listener compiles against Paper 1.21, 26.1,
  and 26.2 adapters
- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with private chat isolation enabled

Release notes carried forward from `v1.1.53`:

- durable item rewards: `ITEM` mission rewards are now deposited by Core into
  the island warehouse instead of relying on an online player event consumer
- offline and failover safe: rewards remain available when the player is
  offline, changes Paper nodes, or reconnects after event-stream delay
- authoritative settlement: mission completion is accepted only after the
  warehouse deposit succeeds; deposit failure reopens the mission for retry
- normalized item input: namespaced and Bukkit-style reward material keys use
  the same normalization rules as ordinary warehouse deposits
- observable delivery: successful item rewards record material, amount, and
  resulting warehouse balance in mission audit fields
- addon consistency: Core publishes both `ISLAND_WAREHOUSE_CHANGED` and
  `ISLAND_MISSION_COMPLETED` after durable item delivery
- rolling-upgrade compatibility: Paper retains the legacy queued-item listener
  for older Core nodes while new nodes emit the durable warehouse reward code
- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with durable warehouse mission rewards enabled

Release notes carried forward from `v1.1.52`:

- retry-safe mission rewards: mission completion is no longer left permanently
  committed when its configured reward cannot be applied
- safe reopening: failed reward settlement moves progress to `goal - 1` and
  clears completion, allowing one click or progress event to retry cleanly
- truthful API responses: reward settlement failures return a conflict response
  instead of reporting a successful mission completion with a missing reward
- repeatable mission fencing: completed repeatable missions reject further
  progress until the current reward has been settled exactly once
- cycle reset: successful repeatable rewards atomically reset progress to zero
  and clear completion before the next cycle can begin
- failure containment: if a reward was applied but repeatable reset fails, the
  mission remains completed so another event cannot duplicate the reward
- cache convergence and audit: reopen/reset mutations refresh Redis projections;
  failed settlement and failed reset paths write explicit operator audit records
- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with reward reopening and repeatable reset enabled

Release notes carried forward from `v1.1.51`:

- combined upgrade prices: one level can require an island-bank amount and any
  number of island-warehouse materials at the same time
- atomic multi-price settlement: bank and item prices are withdrawn in a stable
  order before the expected-level compare-and-set is attempted
- full compensation: insufficient later items, storage errors, or level races
  refund every earlier item in reverse order and then refund the bank amount
- rule-complete menus: new islands now see every configured upgrade, including
  entries that have never been purchased and therefore have no stored row yet
- price-aware GUI: each upgrade shows current/max level, next bank cost, every
  next warehouse-item cost, and a non-clickable maximum-level state
- configuration format: under a level, place one or more materials below
  `item-costs`; namespaced and Bukkit-style material keys are normalized:

  ```yaml
  levels:
    2:
      cost: 10000
      item-costs:
        minecraft:diamond: 16
        EMERALD: 32
      size: 150
  ```

- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with combined price parsing, settlement, and rule-complete views enabled

Release notes carried forward from `v1.1.50`:

- conflict-safe upgrade purchases: every level change now uses an atomic
  expected-level comparison instead of an unconditional overwrite
- multi-Core correctness: concurrent purchases for the same island and upgrade
  can no longer both charge while writing the same resulting level
- automatic payment compensation: a level conflict or storage write failure
  refunds the already withdrawn island-bank cost
- explicit incident codes: refunded and refund-failed outcomes are distinguished
  so operators can alert on the rare case that automatic compensation also fails
- cache convergence: the Redis upgrade projection refreshes from authoritative
  storage after both successful and rejected compare-and-set attempts
- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with the atomic purchase repository active

Release notes carried forward from `v1.1.49`:

- dedicated leash permission: attaching and detaching leads now consistently
  require `LEASH` instead of sharing the broad `INTERACT` permission
- leash-knot protection: right-clicking or attacking a fence knot and breaking
  it through Bukkit's hanging-entity path all consult the same `LEASH` grant
- no combat-policy overlap: attacking a leash knot no longer falls through to
  `ATTACK_MOB`, so role configuration matches the action players perform
- compatibility-preserving visitors: visitor lead access continues to follow
  `VISITOR_INTERACT`, while member and trusted roles can grant it independently
- future-safe persistence: V78's format-based PostgreSQL guards accept the new
  key without another table-constraint migration
- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with the new permission active

Release notes carried forward from `v1.1.48`:

- dedicated item-frame permission: inserting, rotating, damaging, placing, and
  breaking normal or glow item frames now consistently require `ITEM_FRAME`
- no combat-policy overlap: item-frame damage no longer falls through to the
  generic `ATTACK_MOB` permission
- compatibility-preserving visitors: visitor item-frame access continues to
  follow `VISITOR_INTERACT`, while member and trusted roles can grant the new
  permission independently
- extensible PostgreSQL guards: V78 replaces hardcoded permission allowlists
  with trimmed uppercase key-format constraints on roles and player overrides
- migration-safe future permissions: valid new enum keys no longer require a
  database constraint migration, while malformed keys remain rejected
- real topology proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with V78 applied

Release notes carried forward from `v1.1.47`:

- complete SS2 time flags: `ALWAYS_DAY`, `ALWAYS_MIDDLE_DAY`, `ALWAYS_NIGHT`,
  and `ALWAYS_MIDDLE_NIGHT` provide deterministic island-local sky time
- island weather flags: `ALWAYS_RAIN` and `ALWAYS_SHINY` independently select
  rain or clear weather for players currently inside the island
- shared-shard safety: overrides use Paper's per-player time and weather APIs;
  no world time, storm, or neighboring island state is mutated
- bounded hot path: the listener caches each player's effective environment and
  only sends a new override when the state changes
- clean exits: crossing outside an island, changing worlds, or quitting restores
  the player's normal server time and weather
- migration parity: all six matching SuperiorSkyblock2 flag fields are imported

Release notes carried forward from `v1.1.46`:

- SS2-style natural gameplay flags: islands now expose independent
  `CROPS_GROWTH`, `TREE_GROWTH`, `EGG_LAY`, and `GHAST_FIREBALL` controls
- compatibility-safe defaults: existing and migrated islands keep these natural
  behaviors enabled until an operator or island role explicitly disables them
- complete event enforcement: crop material growth, structure growth, chicken
  egg drops, and ghast fireball explosions consult their dedicated local flag
- granular explosion policy: ghast fireballs still require the general explosion
  flag and can additionally be disabled without disabling TNT or creepers
- migration parity: matching SuperiorSkyblock2 flag fields import into the new
  CloudIslands keys without being reported as unsupported legacy data
- complete flag contract: the API manifest now includes gameplay, border, and
  social flags rather than omitting newer keys

Release notes carried forward from `v1.1.45`:

- PostgreSQL permission persistence fixed: V77 expands both role permission and
  player override constraints from the original 30 keys to all 51 runtime keys
- safe live upgrade: existing constraints are dropped and recreated without
  rebuilding permission tables or deleting configured grants
- drift-proof migration import: the SuperiorSkyblock2 scanner now derives its
  supported permission list directly from `IslandPermission.values()`
- complete addon contract: the API JAR manifest advertises every current
  permission key instead of the historical baseline subset
- schema regression gate: every runtime permission must appear in both V77
  constraints, preventing future enum/schema drift
- real database proof: PostgreSQL, Redis, MinIO, and dual-Core Integration passed
  with V77 applied

Release notes carried forward from `v1.1.44`:

- topology-correct publish evidence: a route event observed on the secondary
  Core now proves both primary publication and remote replay
- no false producer failure: outbound events are no longer required to loop
  back into the producer Core's local in-memory event stream
- remote regression proof: the corrected PostgreSQL, Redis, MinIO, and dual-Core
  Integration workflow completed successfully after this change

Release notes carried forward from `v1.1.43`:

- deterministic event evidence: the integration probe independently verifies
  that the primary Core published `ROUTE_TICKET_CREATED` before requiring the
  secondary Core to replay it
- slow-runner resilience: Redis-backed event convergence now has a bounded
  30-second observation window and up to eight controlled route regenerations
- actionable failures: metrics distinguish `eventPublishObserved` from
  `eventReplayObserved`, separating producer faults from cluster replay lag
- real infrastructure proof: the corrected probe passed the remote PostgreSQL,
  Redis, MinIO, and dual-Core Integration workflow after the earlier timeout

Release notes carried forward from `v1.1.42`:

- natural growth restored: disabling `FIRE_SPREAD` no longer disables vines,
  grass, mushrooms, sculk, or other non-fire `BlockSpreadEvent` changes
- explicit fire classification: only `FIRE` and `SOUL_FIRE` source/result
  transitions consult the island fire-spread flag
- material-transition accounting: bamboo saplings, chorus flowers, and other
  `BlockGrowEvent` changes that replace one material with another now update
  island level and worth deltas after cancellation has settled
- dependent-break accounting: Paper `BlockBreakBlockEvent` now records blocks
  broken because their supporting block was removed, including stacked crops,
  buttons, and similar attachments
- duplicate-safe design: the original player block remains handled by
  `BlockBreakEvent`; only dependent blocks use the new Paper event path

Release notes carried forward from `v1.1.41`:

- dispenser boundary containment: empty, water, and lava buckets can no longer
  dispense across an island allocation boundary
- accurate automation accounting: allowed dispenser liquid changes reach level
  deltas only at `MONITOR`, after other plugins have had a chance to cancel
- configurable raid safety: `TRIGGER_RAID` is a dedicated member permission;
  trusted players and visitors cannot start raids unless explicitly promoted
- island mob isolation: hostile target selection now follows the local
  `ATTACK_MOB` decision, preventing visitors from dragging island mobs into
  unauthorized combat paths
- event-surface proof: dispenser, raid, and mob-target listeners are included in
  the synchronous local-cache protection manifest and regression gates

Release notes carried forward from `v1.1.40`:

- projectile pickup closure: arrows and tridents now pass through the same local
  `PICKUP_ITEM` decision as ordinary dropped items, closing a separate Paper
  event path that could bypass visitor pickup policy
- launch-time protection: fishing hooks, tridents, ender pearls, and player wind
  charges are rejected at their source when the matching island permission is
  absent; destination and explosion checks remain as defense in depth
- frost-walker containment: player-attributed frosted-ice formation now requires
  build access instead of bypassing ordinary block-place protection
- additional SS2-style privileges: creeper ignition, entity naming, and sculk
  sensor/shrieker interaction can be delegated independently per island role
- compatible role defaults: members and trusted roles retain existing physical
  interactions, while visitors continue to follow `VISITOR_INTERACT`

Release notes carried forward from `v1.1.39`:

- additional SS2-style privileges: roles now expose `BREAK_SPAWNER`,
  `PAINTING`, `TURTLE_EGG_TRAMPLE`, and `WIND_CHARGE` independently
- spawner safety: breaking a mob spawner no longer inherits every ordinary
  block-break grant and can be restricted to selected roles
- painting control: placing and breaking paintings share a dedicated permission
  while non-painting hanging entities retain existing build/break behavior
- turtle egg protection: physical trampling is distinguished from generic
  interaction and remains compatible with the visitor-interaction flag
- player-attributed wind charges: wind-charge and breeze-wind-charge explosions
  trace the projectile shooter and cancel fully when that player lacks access

Release notes carried forward from `v1.1.38`:

- advanced SS2-style privileges: roles now expose `PICKUP_ENTITY_BUCKET`,
  `TAKE_LECTERN_BOOK`, `DYE_SHEEP`, `SADDLE_ENTITY`, `BRUSH`, `ENDER_PEARL`,
  and `CHORUS_FRUIT` independently
- entity-bucket protection: fish and axolotl capture is checked both during the
  initial entity interaction and the cancellable bucket event
- item-aware protection: dye, saddle, shears, breeding food, and brush actions
  resolve to the intended permission before the generic interaction fallback
- protected book recovery: removing books from lecterns now has a dedicated
  authorization check instead of relying only on opening the lectern
- special teleport control: ender-pearl and chorus-fruit destinations are
  checked against local island permissions and boundaries before teleporting
- compatible defaults: members and trusted roles retain established behavior;
  visitor-compatible item interactions still honor `VISITOR_INTERACT`

Release notes carried forward from `v1.1.37`:

- SS2-style interaction control: island roles now expose independent
  `ANIMAL_BREED`, `ANIMAL_SHEAR`, `FISH`, `ENTITY_RIDE`, and
  `VILLAGER_TRADE` permissions
- event-complete protection: breeding, shearing, fishing, and vehicle entry use
  their dedicated Paper events instead of relying on a generic right click
- item-aware entity actions: shears and valid breeding food are classified
  before generic entity interaction so custom roles work as configured
- ride and trade separation: boats, minecarts, horses, steerable entities, and
  villagers no longer share one broad interaction permission
- compatible defaults: members and trusted players retain access, while the
  existing visitor-interaction flag continues to govern visitor compatibility

Release notes carried forward from `v1.1.36`:

- complete player route cleanup: administrative and recovery clears now remove
  every historical ticket for the player instead of only one latest record
- distributed cache correctness: player route keys and matching ticket-ID keys
  are removed from shared Redis alongside the authoritative SQL deletion
- no stale route reuse: consumed or failed tickets can no longer reappear on a
  different Core instance after a clear and suppress creation of a fresh route
- observable cleanup: route-clear responses, audit records, and global events
  include the exact number of tickets removed
- deterministic cluster proof: the integration load probe waits for the primary
  Core to observe ticket absence before measuring cross-Core event replay

Release notes carried forward from `v1.1.35`:

- granular SS2-style control: island roles now expose a dedicated `FERTILIZE`
  permission instead of treating every bone-meal action as generic interaction
- default compatibility: members and trusted players retain fertilization access,
  while visitors and unknown/custom roles remain denied until explicitly allowed
- allocation safety: fertilized trees and vegetation are cancelled atomically
  when any generated block would cross the authoritative island boundary
- natural growth safety: non-bone-meal structure growth follows the same island
  boundary rule, preventing trees from entering neighboring allocation cells
- accurate progression: every allowed generated block is reported through the
  existing block replacement delta path for level and worth recalculation

Release notes carried forward from `v1.1.34`:

- safe boundary recovery: players crossing an island edge now return through
  the same solid-ground, clearance, liquid, and hazard validation as homes and
  route tickets instead of teleporting blindly to Y 100 or the current height
- bounded island ownership: recovery candidates remain inside the island's
  authoritative region and cannot spill into a neighboring allocation cell
- non-blocking recovery: destination chunks are prepared asynchronously before
  inspecting blocks or moving the player
- event-storm protection: each player can have only one pending boundary return,
  preventing repeated move events from scheduling duplicate chunk work
- safe failure: when no valid return point exists, movement remains cancelled
  and the player receives a localized Korean or English explanation

Release notes carried forward from `v1.1.33`:

- correct single-Paper coordinates: local home and warp destinations now use
  the target island UUID's registered origin instead of the player's current
  lobby or island position
- safe arrivals: home, warp, visit, and route-ticket movement validates solid
  footing, two-block clearance, liquids, and hazardous blocks before teleport
- bounded recovery: blocked destinations search only four blocks horizontally
  and eight blocks vertically, never escaping the authoritative island region
- non-blocking chunk preparation: every chunk touched by the safety search is
  loaded asynchronously before Bukkit block inspection begins
- truthful results: missing regions, unsafe destinations, and rejected
  teleports return localized failures instead of reporting false success

Release notes carried forward from `v1.1.32`:

- real world application: accepted biome changes now repaint the island region
  on its assigned Paper island node instead of changing Core metadata only
- bounded runtime work: chunks load asynchronously and one completed chunk is
  painted per tick so a large island does not create one long main-thread stall
- complete biome volume: Paper's four-block biome sample grid is updated across
  every intersecting chunk and the world's full vertical build range
- latest change wins: each island has a generation token, so a newer biome
  event supersedes any older paint batch still waiting on chunk work
- guarded execution: missing local regions, unloaded worlds, unknown registry
  entries, and failed chunk loads produce operator warnings without unsafe work

Release notes carried forward from `v1.1.31`:

- full safe biome catalog: the supported environment list grows from ten
  hand-picked entries to all 63 selectable vanilla 1.21 biomes
- hazardous-world guard retained: `minecraft:the_void` remains rejected by the
  shared API policy and Core mutation endpoint
- complete GUI access: the expanded catalog is split across bounded pages
  instead of silently stopping at the first 17 menu slots
- consistent command discovery: tab completion and GUI selection consume the
  same shared biome policy, including Nether, End, cave, ocean, and peak biomes
- authoritative navigation: biome page actions retain the island UUID and clamp
  against the current shared catalog

Release notes carried forward from `v1.1.30`:

- truthful TOP 10 views: level, worth, and review ranking requests no longer
  hide rank ten behind their nine configured metric slots
- synchronized pages: all three ranking columns advance together while keeping
  each entry's original global rank and island visit target
- dynamic bounds: sparse review or worth data cannot create an unreachable page;
  the longest returned ranking column determines the current page range
- page-safe visits: entries on the second page retain the same typed island
  target action as entries on the first page
- localized navigation: previous and next ranking controls are available in
  Korean and English configurations

Release notes carried forward from `v1.1.29`:

- unbounded public-warp browsing: Paper now requests stable Core offset pages
  instead of making every category and search stop at its first 45 results
- server-side filtering order: public-access, island-lock, and PUBLIC_WARPS flag
  checks are applied before offset and page limits so pages cannot contain gaps
- efficient next-page detection: each GUI request fetches one sentinel entry
  beyond its configured slots without loading the full public warp catalog
- filter continuity: category and query values remain attached to typed previous
  and next actions across every public-warp page
- compatible client contract: the existing public-warp query method remains
  available while the new offset overload enables scalable consumers

Release notes carried forward from `v1.1.28`:

- complete fetched audit history: the Paper log GUI no longer discards entries
  28 through 100 returned by Core after rendering its first inventory page
- bank-filter continuity: deposit and withdrawal-only views retain their filter
  across every page instead of falling back to the general island audit stream
- authoritative navigation: log page actions carry the island UUID, normalized
  ALL or BANK mode, and page number through the typed GUI action parser
- stable ordering: displayed entry numbers retain their position in the fetched
  history instead of restarting at one on each page
- localized navigation: previous and next log-page controls are provided in
  Korean and English configurations

Release notes carried forward from `v1.1.27`:

- complete upgrade catalog: every configured Core upgrade remains visible and
  purchasable even when the catalog exceeds the first 45 GUI slots
- complete limit management: custom, integration, and operator-defined island
  limits beyond the first page remain adjustable with normal and Shift steps
- authoritative navigation: both page actions carry the island UUID so a page
  transition cannot silently switch to another active-island context
- dynamic bounds: runtime catalog changes clamp the requested page against the
  newest Core upgrade or limit result
- localized navigation: Korean and English previous/next controls are included
  for both menus

Release notes carried forward from `v1.1.26`:

- complete named-home access: islands with more than one GUI page of homes can
  browse, teleport to, and update every configured destination
- complete private-warp management: private and public state, teleport, toggle,
  and Shift-right-click deletion remain available beyond the first 45 warps
- authoritative navigation: home and private-warp page actions retain the
  island UUID instead of resolving a possibly changed current-island context
- dynamic bounds: removing destinations while a menu is open clamps navigation
  against the latest Core home or warp catalog
- localized navigation: previous and next controls are provided in Korean and
  English for both management menus

Release notes carried forward from `v1.1.25`:

- complete warehouse capacity: the Paper warehouse now exposes every configured
  row, including all 54 material entries available at the six-row limit
- no hidden tail: page controls replace the previous 44-slot rendering ceiling
  that silently omitted the final ten materials at maximum capacity
- authoritative island context: warehouse navigation carries the island UUID
  and normalized page in typed GUI actions before withdrawals are processed
- dynamic bounds: capacity or inventory changes clamp navigation against the
  latest Core result while preserving the configured row-based item limit
- localized navigation: previous and next warehouse-page controls are available
  in Korean and English configurations

Release notes carried forward from `v1.1.24`:

- complete progression catalog: Paper no longer hides mission or challenge
  definitions beyond the first 44 task slots
- page-safe completion: progress, rewards, repeatability, daily reset state,
  and completion actions remain available on every task page
- authoritative task context: navigation carries both the island UUID and the
  normalized MISSION or CHALLENGE kind to prevent cross-island/type mistakes
- dynamic bounds: definition enablement or provider reloads clamp the current
  page against the latest Core mission result
- localized navigation: previous and next task-page controls are available in
  Korean and English configurations

Release notes carried forward from `v1.1.23`:

- complete template catalog: the Paper island-creation menu no longer drops
  enabled templates after its first 14 slots
- permission-aware comparison retained: locked templates remain visible as
  barriers with their required permission on every page instead of disappearing
- confirmation integrity: selecting a template from any page still reloads its
  current Core metadata before the dedicated create confirmation screen
- dynamic page bounds: enabling, disabling, adding, or removing templates while
  the menu is open clamps navigation against the latest enabled catalog
- localized navigation: previous and next template controls are available in
  Korean and English menu configurations

Release notes carried forward from `v1.1.22`:

- complete custom-role management: islands with more than 17 built-in and
  custom roles can browse and manage the entire role catalog in Paper
- page-safe editing: left/right weight adjustment and Shift default restoration
  remain available on every role page
- authoritative context: role page actions carry the island UUID so navigation
  cannot accidentally reopen or modify another island's role catalog
- dynamic bounds: role deletion or insertion while the menu is open clamps the
  requested page against the latest Core result
- localized navigation: previous and next role-page controls are provided in
  both Korean and English configurations

Release notes carried forward from `v1.1.21`:

- reliable cross-Core certification: the integration load probe now tolerates
  Redis subscriber startup races by producing a bounded number of fresh route
  events while it waits for secondary-Core replay
- evidence remains strict: success still requires the secondary Core to expose
  an actual `ROUTE_TICKET_CREATED` event; retries never convert missing replay
  into a warning or synthetic pass
- bounded recovery: replay production is limited to four total attempts within
  the existing ten-second evidence window
- release-gate enforcement: static policy checks require the bounded retry,
  attempt counter, and replay evidence fields to remain wired
- live verification: the corrected probe passed the GitHub PostgreSQL, Redis,
  MinIO, and dual-Core Integration workflow before this release was prepared

Release notes carried forward from `v1.1.20`:

- complete public discovery: the Paper visit GUI can browse every public,
  unlocked island instead of permanently stopping at its first 35 slots
- stable Core pages: public-list queries use deterministic creation-time and
  island-ID ordering with bounded server-side offset and limit parameters
- random behavior preserved: random visit continues using the randomized
  candidate query while user-facing pages remain stable between clicks
- next-page sentinel: Paper requests only one extra record to determine whether
  another page exists instead of loading the entire public island catalog
- shrinking-list recovery: if islands become private or locked while a player
  navigates, an empty trailing page automatically returns to the prior page
- typed API compatibility: the navigation client exposes offset pagination with
  a backward-compatible default for third-party client implementations

Release notes carried forward from `v1.1.19`:

- complete recovery history: the Paper snapshot GUI queries the configured
  retention horizon instead of permanently limiting recovery to 20 records
- default-policy coverage: all 85 snapshots retained by the default hourly,
  daily, weekly, and manual policy remain reachable from the GUI
- paged restore workflow: operators can inspect metadata and initiate the
  existing Shift+right-click restore confirmation from every snapshot page
- safe upper bound: unusually large retention configurations are bounded to
  100 records per GUI query to protect the Paper server and Core API
- dynamic page clamping: pruning or creating snapshots while navigating cannot
  leave players on an invalid page

Release notes carried forward from `v1.1.18`:

- complete visitor-ban management: Paper operators can page through more than
  45 active visitor bans without older entries becoming unreachable
- authoritative ban navigation: previous/next actions preserve the island UUID
  so detail inspection and pardon operations stay scoped to the correct island
- complete invitation inbox: players can page through every pending island
  invitation and accept or decline entries beyond the first GUI page
- bounded page safety: requested pages are clamped against current list sizes,
  including lists that shrink after a pardon, acceptance, decline, or expiry
- localized controls: ban and invitation page navigation is available in both
  Korean and English menu configurations

Release notes carried forward from `v1.1.17`:

- Paper island-list workflow: left-click still visits an island while
  right-click safely selects it as the player's primary island
- complete list navigation: the Paper “my islands” GUI paginates beyond 45
  owned, member, and co-op islands instead of silently truncating the result
- visible routing state: every island entry identifies whether it is the
  currently selected primary island before the player visits or changes it
- localized guidance: English and Korean lore explains visit, selection, and
  previous/next page controls

Release notes carried forward from `v1.1.16`:

- multi-island selection: `/is select <player|island>` (plus `switch`, `선택`,
  and `섬선택`) lets Paper and Velocity players choose the island used by
  subsequent home routing
- server-authoritative safety: the player selection endpoint accepts only an
  owned island or an active member role and rejects visitor, banned, stale, and
  unrelated island projections without changing the stored selection
- single-Paper continuity: the same selected-island profile drives lobby home
  routing when Paper is deployed without Velocity
- membership projection hardening: player island lists omit BANNED and VISITOR
  rows while restoring an authoritative owner entry if membership projection
  data is incomplete

Release notes carried forward from `v1.1.15`:

- member home routing: `/is home` resolves the player's selected island when
  they are a MEMBER or CO_OWNER instead of requiring island ownership
- safe selection validation: stale or administrator-set primary island IDs are
  accepted only when the player still owns or belongs to that island, then fall
  back to an owned or valid member island
- invite continuity: accepting the first island invite initializes an empty
  primary island selection without overwriting an existing selection
- single-Paper lobby support: `/is home` outside an island region now creates a
  Core home route ticket and uses direct-local routing instead of returning the
  false “must be on an island” error
- local precision retained: home commands issued inside an active island still
  use the existing named home coordinates, permission checks, and Core outage
  fallback behavior

Release notes carried forward from `v1.1.14`:

- atomic invite capacity: concurrent invite acceptance locks the durable island
  row and checks the team limit inside the same transaction as invite state and
  membership mutation
- consistent local semantics: the in-memory repository serializes the same
  operation, with a concurrency regression test proving only one final slot can
  be consumed
- correct team-slot accounting: temporary `TRUSTED` co-op access does not use a
  permanent team slot, while accepting promotion to `MEMBER` consumes one
  atomically; custom team roles remain included
- actionable race feedback: an invite that loses a capacity race returns
  `MEMBER_LIMIT` instead of the misleading generic unavailable response
- visitor sanction boundaries: authoritative owners and permanent team members
  cannot be processed through visitor ban or visitor kick commands even when a
  membership projection is stale

Release notes carried forward from `v1.1.13`:

- synchronized island visibility: public/private changes now update the durable
  island row, in-memory island snapshot, and Redis island summary immediately
- consistent read behavior: island information and catalog responses no longer
  retain an old `publicAccess` value after the access command succeeds
- ownership transfer continuity: the former owner remains a co-owner without
  losing their selected island, so home and target-resolution commands continue
  to work after transfer
- authoritative owner protection: stale or missing membership-role projections
  cannot allow player or administrator member-removal paths to remove the real
  owner
- membership cleanup: leaving or being kicked clears the removed island as the
  player's selected island only when it was actually selected, without changing
  an unrelated selection

Release notes carried forward from `v1.1.12`:

- corrected warehouse authorization: deposits, withdrawals, menus, and list
  views now consistently require `OPEN_CONTAINER`; bank withdrawal authority
  no longer grants access to island inventory
- protected warehouse contents: visitors without the island container role
  permission can no longer inspect warehouse materials or quantities
- serialized inventory mutations: overlapping requests from one player remain
  locked until the Paper inventory grant or refund is applied, preventing
  capacity races, dropped overflow, and refund ordering bugs
- projection-drift recovery: the authoritative island owner retains access to
  locked islands and private warps even when a membership or stale self-ban
  projection is temporarily inconsistent
- routing correctness: owners with a missing membership projection are never
  classified or allocated as visitor warp traffic

Release notes carried forward from `v1.1.11`:

- fail-closed permission mutation: unknown permission names no longer silently
  become `BUILD` on Velocity or Paper admin command paths
- fail-closed flag mutation: unknown player flags no longer become `FLY`, and
  unknown administrator island flags no longer become `VISITOR_INTERACT`
- strict boolean intent: malformed allow/deny values no longer silently become
  `false`; accepted values include explicit true/false, on/off, allow/deny,
  numeric, and localized forms
- operator feedback and regression coverage: invalid permission, override, and
  flag requests stop before Core mutation and return actionable localized errors

Release notes carried forward from `v1.1.10`:

- canonical block-value semantics: `/is value [material]` now executes on
  Paper so the no-argument form reads the real item in the player's main hand
  instead of being confused with the island's total worth
- bank transaction history: `/is bank logs` opens a bounded GUI containing
  only real deposit and withdrawal audit entries, excluding unrelated island
  activity while retaining log-detail actions
- real team-chat mode: `/is teamchat`, `/is tc toggle`, and explicit `on`/`off`
  now control a per-player mode; normal chat is removed from global viewers and
  sent through the Core `TEAM` channel to authorized members across nodes
- async and lifecycle safety: team-mode chat returns to the Paper scheduler
  before reading location state, and mode state is cleared on disconnect and
  plugin shutdown

Release notes carried forward from `v1.1.9`:

- safe proxy-to-Paper execution: commands that depend on Vault balances,
  Bukkit inventory, the player's exact location, inventory GUIs, or live flight
  state are forwarded to the attached Paper server instead of being approximated
  by Velocity
- economy and inventory correctness: bank deposit/withdraw and warehouse
  deposit/withdraw now retain Paper's permission, idempotency, refund, capacity,
  and real item/money transaction boundaries when `/is` is registered globally
- canonical GUI behavior: `chest`, `vault`, and `warehouse` open the existing
  inventory menu; `warps`, `visitors`, `settings`, `permissions`, `upgrade`,
  `top`, and other official menu commands preserve their Paper GUI behavior
- extension compatibility: targeted read-only CloudIslands commands remain on
  Velocity while no-argument SuperiorSkyblock2 menu forms are delegated to
  Paper, including custom configured root aliases

Release notes carried forward from `v1.1.8`:

- executable Velocity parity: every player alias advertised by the shared
  Paper/Velocity registry now has a proxy execution branch, with regression
  coverage preventing catalog-only commands
- restored SuperiorSkyblock2-style proxy behavior for reviews and ratings,
  visitor statistics, social fields, border controls, stacked-block display,
  co-op aliases, role menus, permission exceptions, chat menus, and warp delete
- current-island correctness: member, access, permission, lifecycle, biome,
  home, and warp actions resolve the player's current island before calling
  Core instead of forwarding the nil current-island sentinel
- safer proxy mutations: unresolved review targets no longer reach Core, and
  standalone team-chat toggle requests no longer become empty chat messages

Release notes carried forward from `v1.1.7`:

- canonical SuperiorSkyblock2 player compatibility: `toggleblocks`, `counts`,
  `balance`/`bal`/`money`, `show`, `team`/`showteam`/`online`,
  `teleport`/`tp`/`go`, `panel`/`manager`/`cp`, and `coops` now work without
  enabling the temporary legacy-alias migration switch
- target-safe Paper and Velocity queries: UUID, exact island name, and player
  primary-island resolution are consistent for balances, island information,
  member lists, values, and block counts; unresolved targets never fall back to
  the caller's island
- migration cutover safety: `/is admin <command>` is recognized, mapped to
  guarded `/ciadmin` guidance, separately metered, and `/ciadmin doctor` warns
  when SS2 migration is enabled without legacy aliases during the transition
- proxy correctness: current-island member queries resolve the player's island
  before calling Core instead of sending a nil UUID

Release notes carried forward from `v1.1.6`:

- deterministic route replay certification: the Core load probe clears the
  reused player's route ticket/session before capturing the event baseline, so
  every run generates and observes a real `ROUTE_TICKET_CREATED` replay
- release-gate regression protection: `verifyReleaseGateCoverage` now enforces
  that route clearing occurs before the event sequence baseline is captured
- verified in GitHub Actions: the corrected main Integration workflow passes
  the full Core integration smoke with PostgreSQL, Redis, and MinIO

Release notes carried forward from `v1.1.5`:

- targeted SS2 query parity: `balance`, `show`, and `team` preserve their
  optional island/player target instead of silently using the caller's island
- shared target resolution: UUID, exact island name, then player primary island
  are resolved through typed Core clients with localized not-found feedback
- targeted warp parity: migration-mode `warp <player|island> [warp]` supports
  names and UUIDs and no longer misreads the target as a local warp name
- permissions remain centralized: synthetic migration subcommands map back to
  the existing bank, menu, member, and warp permission nodes

Release notes carried forward from `v1.1.4`:

- safe SS2 co-op removal: `/is uncoop` and `/is untrust` now require an active
  `TRUSTED` target before mutation and cannot remove permanent team members
- block-value command parity: `/is value [material]` resolves the named or held
  material through typed Core block values and reports worth, level points, and
  placement limit instead of opening an unrelated values list
- player alias collision repair: official `join`, `add`, and `remove` aliases no
  longer get intercepted as legacy admin commands
- broader command migration: common SS2 forms such as `bal`, `money`, `vault`,
  `setbiome`, `tp`, `go`, `settp`, `setgo`, `show`, `showteam`, `online`, `tc`,
  `leader`, `leadership`, and `expel` preserve their player-command intent

Release notes carried forward from `v1.1.3`:

- broader SS2 PlaceholderAPI parity: biome, active ban count/list, default
  home/world coordinates, warp count/limit, and dynamic upgrade levels now use
  typed Core data instead of unavailable local Paper state
- permission parity: dynamic `permission_<permission>` applies player override,
  role rule, and built-in role defaults in the same order as runtime protection
- flag parity: dynamic `flag_<flag>` accepts the runtime's supported true/allow/
  enabled/on values while unknown and disabled flags remain false
- failure isolation: optional biome, ban, home, warp, upgrade, permission, or
  flag query failures leave the base island snapshot usable

Release notes carried forward from `v1.1.2`:

- SS2 scoreboard compatibility: formatted, integer, and raw bank/worth/level
  aliases, leader name, indexed team members, and online team size now resolve
  with deterministic locale-independent output
- resilient island selection: a permanent primary team island wins over stale
  temporary co-op state, while the profile primary remains a safe fallback when
  the membership request is temporarily unavailable
- high-concurrency PlaceholderAPI load: island, bank, member, and limit reads
  are coalesced per island for the cache window, preventing large teams from
  multiplying identical Core requests

Release notes carried forward from `v1.1.1`:

- player island loading: Core now preserves each membership's canonical role
  and temporary expiry in `/v1/players/islands`, fixing blank roles and incorrect
  primary-island selection for member and co-op pages
- PlaceholderAPI parity: owner, permanent member, and temporary co-op players
  resolve a deterministic associated island; role, team/co-op counts, limits,
  lists, lock state, and timestamps are exposed with stable missing values
- runtime load: ranking requests are coalesced into one shared 15-second cache
  instead of issuing a global top-100 query for every player's refresh

Release notes carried forward from `v1.1.0`:

- mission parity and runtime load: definition-driven missions now progress from
  enchanting, Bukkit statistic increments, advancements, and item consumption;
  a bounded two-second per-island cache coalesces Core definition reads during
  high-frequency mining and farming events, and invalidates on completion
- release publishing: matching `v*` tags now verify the README version, rebuild
  all signed evidence artifacts, and publish bundles, individual plugin/addon
  jars, checksums, SBOM, provenance, and generated changelog to GitHub Releases
- addon command SDK: addons can register lifecycle-safe `/is` subcommands with
  permissions, argument validation, asynchronous execution, tab completion,
  help integration, collision rejection, and automatic unregister cleanup
- temporary co-op integrity: permanent members can no longer be overwritten by
  expiring `TRUSTED` membership, existing temporary co-op access can be renewed,
  SS2 `uncoop` removes that access, and admin co-op limits enforce the dedicated
  `ROLE_LIMIT:TRUSTED` capacity instead of consuming the permanent team limit;
  `untrust` removes co-op access and `coops` opens the management surface. The
  public API exposes typed co-op lookup/mutation helpers and defaults to eight
  co-op slots when an island has no explicit override
- Core migration compatibility: SuperiorSkyblock2 import remains a CloudIslands
  Core/Paper/Velocity operation; `cloudislands-satis` no longer exposes legacy
  import or rollback commands
- supported Paper version: Paper `1.21.x` and stable `26.1.x` are
  release-supported; Paper `26.2.x` is compile-only experimental until a stable
  Paper build is available
- breaking changes: no public API breaking change from the 1.0.x compatibility
  baseline; addon compatibility is checked by `apiCompatibilityCheck`
- release checklist: `./gradlew build distBundle distChecksums distSbom
  distProvenance` plus `verifyReleaseSecurityGate`, release cluster evidence,
  and README version-table verification

## Project status

Current read: production-readiness baseline `v1.1.123`.

CloudIslands now has a release cluster evidence gate for the distributed shape:
two Core instances, shared PostgreSQL, Redis, object storage, Paper boot smoke,
Velocity boot smoke, virtual-player route/session coverage, backup/restore, and
failure-injection evidence links. Operators should still run deployment-specific
acceptance for live player traffic, vendor plugin farms, and server-specific
world interactions before opening a public network.

### Assessment

| Area | Score | Notes |
|---|---:|---|
| Architecture and domain model | 8/10 | logical islands and runtime nodes are separated |
| Feature scope | 9/10 | routing, snapshots, permissions, economy, missions, ranking, generator, GUI |
| Distributed consistency | 8/10 | release cluster evidence covers multi-Core, recovery, backup, and route handoff |
| Security hardening | 8/10 | admin permissions, mTLS trusted proxy boundaries, and production fallback checks are enforced |
| Maintainability | 7/10 | route, config, migration, GUI, and runtime certification coverage gates are in place |
| Test and release readiness | 8/10 | unit, compatibility, boot smoke, real infrastructure, and release evidence gates are wired |

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

### Production GA gate

| Gate item | Status | Current read |
|---|---|---|
| Redis lock unlock must use atomic compare-and-delete | MITIGATED | activation and player creation locks use Lua compare-and-delete |
| Redis outage must not silently fall back to per-process local locks in multi-Core mode | MITIGATED | local fallback is disabled by default and release evidence includes multi-Core failure drills |
| Core API auth must not trust client-provided permission headers | MITIGATED | admin permissions are configured server-side |
| mTLS-by-header requires trusted proxy boundaries | MITIGATED | `MtlsHeaderGuard` checks a trusted proxy allowlist |
| production mode must reject non-durable in-memory fallback | MITIGATED | startup validation always blocks non-durable fallback in production |
| route failure cleanup should be one idempotent path | MITIGATED | route-ticket failure transitions are conditional and return each failed ticket once across repeated cleanup |
| coordinate fallback should fail closed when placement data is missing | MITIGATED | workflows, route activation, and job completion reject missing placement before issuing jobs, tickets, or ACTIVE runtimes |
| Gradle Wrapper, CI, release binaries, checksums | MITIGATED | wrapper, GitHub Actions, dist bundles, and SHA-256 checksums exist |
| SBOM, provenance, vulnerability gate | MITIGATED | CI runs dependency review and release builds generate SBOM plus provenance artifacts |
| real PostgreSQL, Redis, MinIO integration | MITIGATED | `ciIntegrationSmoke` runs with these services |
| multi-Core, multi-version boot, API compatibility gate | MITIGATED | matrix tasks, `ciIntegrationSmoke`, `releaseClusterSmokeGate`, and `apiCompatibilityCheck` are wired |

### Deployment constraints

Recommended constraints:

- run at least two Core instances against shared durable backends
- Core bound to loopback, a private internal network, or a trusted reverse proxy boundary
- reverse proxy strips and rewrites security headers
- admin API only on a private management network
- in-memory fallback disabled
- Redis, database, and object storage not public
- DB backup and island snapshot restore rehearsed with `releaseClusterSmokeGate`

### Critical files

- `RedisActivationLock`
- `RedisPlayerCreationLock`
- `MtlsHeaderGuard`
- `AdminEndpointGuard`
- `RouteTicketConsumer`
