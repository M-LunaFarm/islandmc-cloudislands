# CloudIslands

Distributed Skyblock platform for Velocity and Paper networks.

Version: `1.1.35`

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
| lifecycle/templates/homes/warps/visits | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies cross-Core create, job, route, session, consume; Paper tests verify target-island coordinates and bounded safe destinations | 26.1.2 is boot-verified; 26.2 stays compile-only until a stable Paper build is available |
| access/bans/membership/roles/permissions | IMPLEMENTED_VERIFIED | Core API and permission event replay are exercised in tests | third-party permission plugins are integration-status reported, not all boot-verified |
| flags/protection | IMPLEMENTED_VERIFIED | unit verified; Paper policy tests cover fertilization growth boundaries and bounded asynchronous safe returns | runtime grief/protection scenarios need manual or fixture-backed Paper interaction tests |
| ranking/level/worth/block values | IMPLEMENTED_VERIFIED | service-level verified | worth economics beyond configured value calculations are not release-certified |
| upgrades/size/border/biome | IMPLEMENTED_VERIFIED | verifyUpgradeEffectCoverage covers Core upgrade effects and biome normalization; Paper tests cover world-border policy and chunk-batched biome painting | operator deployment acceptance is still recommended; CI verifies Core mutation plus cancellable, asynchronous Paper biome painting and border application policy |
| bank/economy/missions/challenges/generators/limits | IMPLEMENTED_VERIFIED | verifyMissionEventProgress covers block, farm, kill, fishing, crafting, enchanting, statistic, advancement, and item-consumption progress plus the bounded definition cache; reward, generator, and economy safety gates cover the remaining scope | brewing completion has no reliable Bukkit actor and is intentionally not guessed; operator live-server economy/provider acceptance is still recommended |
| chat/logs/reviews | IMPLEMENTED_VERIFIED | verifyReviewModerationCoverage plus Core audit/visitor route tests cover current workflow | live multi-player chat moderation acceptance is deployment-specific outside unit CI |
| snapshots/rollback/migration/recovery | IMPLEMENTED_VERIFIED | ciIntegrationSmoke verifies recovery restore with shared services | releaseClusterSmokeGate now includes database backup, object bundle, manifest checksum, restore, route, and audit evidence |
| Java API/events/addons | IMPLEMENTED_VERIFIED | apiCompatibilityCheck verifies release contract metadata and the public API signature baseline | external addon certification depends on testkit evidence supplied by the addon |
| integrations/localization/GUI | PARTIAL_VERIFIED | verifyIntegrationRuntimeSmoke verifies executable runtime services and keeps probe-only external adapters diagnostic | Vault and PlaceholderAPI runtime services are executable; external lifecycle and state-transfer operations remain diagnostic until real executors exist |
<!-- feature-parity:end -->

## Release

Current release: `v1.1.35`

Built for the CloudIslands 1.1.35 baseline.

Release notes for `v1.1.35`:

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

Current read: production-readiness baseline `v1.1.35`.

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
