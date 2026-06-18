# CloudIslands

Distributed Skyblock platform for Velocity and Paper networks.

Version: `1.0.0`

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

## Commands

Command help is designed as:

```text
1 line > 1 command
```

Paging supported.

Player commands stay logical.
No `Island-2` exposure. No route ticket exposure.

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

## Release

Current release: `v1.0.0`

Built for the CloudIslands 1.0.0 baseline.
