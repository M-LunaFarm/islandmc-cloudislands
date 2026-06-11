package kr.lunaf.cloudislands.paper.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.CloudIslandsApi;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLevelSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.api.model.RoutePlan;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;

public final class PaperCloudIslandsApi implements CloudIslandsApi {
    private final QueryService query;
    private final PlayerService players;
    private final RoutingService routing;
    private final PermissionService permissions;
    private final RuntimeService runtime;
    private final AdminService admin;
    private final CommandService commands;

    public PaperCloudIslandsApi(CoreApiClient client, CloudIslandsPaperAgent agent) {
        this.query = new QueryService(client);
        this.players = new PlayerService(query);
        this.routing = new RoutingService(client);
        this.permissions = new PermissionService(agent);
        this.runtime = new RuntimeService(client);
        this.admin = new AdminService(client);
        this.commands = new CommandService(client);
    }

    @Override
    public IslandQueryService islands() {
        return query;
    }

    @Override
    public PlayerIslandService players() {
        return players;
    }

    @Override
    public IslandRoutingService routing() {
        return routing;
    }

    @Override
    public IslandPermissionService permissions() {
        return permissions;
    }

    @Override
    public IslandRuntimeService runtime() {
        return runtime;
    }

    @Override
    public IslandAdminService admin() {
        return admin;
    }

    @Override
    public IslandCommandService commands() {
        return commands;
    }

    private static final class QueryService implements IslandQueryService {
        private final CoreApiClient client;

        private QueryService(CoreApiClient client) {
            this.client = client;
        }

        @Override
        public CompletableFuture<Optional<IslandSnapshot>> getIsland(UUID islandId) {
            return client.islandInfo(islandId).thenApply(PaperCloudIslandsApi::island);
        }

        @Override
        public CompletableFuture<Optional<IslandSnapshot>> getIslandByOwner(UUID ownerUuid) {
            return client.islandInfoByOwner(ownerUuid).thenApply(PaperCloudIslandsApi::island);
        }

        @Override
        public CompletableFuture<IslandRuntimeSnapshot> getRuntime(UUID islandId) {
            return client.adminIslandWhere(islandId).thenApply(PaperCloudIslandsApi::runtime);
        }

        @Override
        public CompletableFuture<List<IslandMemberSnapshot>> getMembers(UUID islandId) {
            return client.listIslandMembers(islandId).thenApply(PaperCloudIslandsApi::members);
        }

        @Override
        public CompletableFuture<List<IslandHomeSnapshot>> getHomes(UUID islandId) {
            return client.listIslandHomes(islandId).thenApply(PaperCloudIslandsApi::homes);
        }

        @Override
        public CompletableFuture<List<IslandWarpSnapshot>> getWarps(UUID islandId) {
            return client.listIslandWarps(islandId).thenApply(PaperCloudIslandsApi::warps);
        }

        @Override
        public CompletableFuture<List<IslandPermissionRuleSnapshot>> getPermissionRules(UUID islandId) {
            return client.listIslandPermissions(islandId).thenApply(PaperCloudIslandsApi::permissionRules);
        }

        @Override
        public CompletableFuture<List<IslandBanSnapshot>> getBans(UUID islandId) {
            return client.listIslandBans(islandId).thenApply(PaperCloudIslandsApi::bans);
        }

        @Override
        public CompletableFuture<List<IslandInviteSnapshot>> getPendingInvites(UUID playerUuid) {
            return client.listPendingInvites(playerUuid).thenApply(PaperCloudIslandsApi::invites);
        }

        @Override
        public CompletableFuture<IslandFlagsSnapshot> getFlags(UUID islandId) {
            return client.listIslandFlags(islandId).thenApply(PaperCloudIslandsApi::flags);
        }
        @Override
        public CompletableFuture<IslandBiomeSnapshot> getBiome(UUID islandId) {
            return client.islandBiome(islandId).thenApply(PaperCloudIslandsApi::biome);
        }

        @Override
        public CompletableFuture<List<IslandLimitSnapshot>> getLimits(UUID islandId) {
            return client.listIslandLimits(islandId).thenApply(PaperCloudIslandsApi::limits);
        }

        @Override
        public CompletableFuture<IslandLevelSnapshot> getLevel(UUID islandId) {
            return client.islandInfo(islandId).thenApply(PaperCloudIslandsApi::level);
        }
        @Override
        public CompletableFuture<List<IslandUpgradeSnapshot>> getUpgrades(UUID islandId) {
            return client.listIslandUpgrades(islandId).thenApply(PaperCloudIslandsApi::upgrades);
        }

        @Override
        public CompletableFuture<List<IslandMissionSnapshot>> getMissions(UUID islandId, String kind) {
            return client.listIslandMissions(islandId, kind).thenApply(PaperCloudIslandsApi::missions);
        }

        @Override
        public CompletableFuture<List<IslandSnapshotRecord>> getSnapshots(UUID islandId, int limit) {
            return client.listIslandSnapshots(islandId, limit).thenApply(PaperCloudIslandsApi::snapshots);
        }

        @Override
        public CompletableFuture<List<IslandLogRecord>> getLogs(UUID islandId, int limit) {
            return client.listIslandLogs(islandId, limit).thenApply(PaperCloudIslandsApi::logs);
        }
        @Override
        public CompletableFuture<IslandBankSnapshot> getBank(UUID islandId) {
            return client.islandBank(islandId).thenApply(PaperCloudIslandsApi::bank);
        }
    }

    private static final class PlayerService implements PlayerIslandService {
        private final QueryService query;

        private PlayerService(QueryService query) {
            this.query = query;
        }

        @Override
        public CompletableFuture<Optional<UUID>> getOwnedIslandId(UUID playerUuid) {
            return query.getIslandByOwner(playerUuid).thenApply(island -> island.map(IslandSnapshot::islandId));
        }

        @Override
        public CompletableFuture<Boolean> hasIsland(UUID playerUuid) {
            return getOwnedIslandId(playerUuid).thenApply(Optional::isPresent);
        }

        @Override
        public CompletableFuture<List<IslandSnapshot>> getJoinedIslands(UUID playerUuid) {
            return query.getIslandByOwner(playerUuid).thenApply(island -> island.map(value -> List.of(value)).orElseGet(List::of));
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> getProfile(UUID playerUuid) {
            return getOwnedIslandId(playerUuid)
                .thenApply(islandId -> Optional.of(new PlayerIslandProfile(playerUuid, "", islandId, Instant.EPOCH)));
        }
    }

    private static final class RoutingService implements IslandRoutingService {
        private final CoreApiClient client;

        private RoutingService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid) { return client.createHomeTicket(playerUuid); }
        @Override public CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName) { return client.createHomeTicket(playerUuid, homeName); }
        @Override public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId) { return client.createVisitTicket(visitorUuid, targetIslandId); }
        @Override public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) { return client.createRandomVisitTicket(visitorUuid); }
        @Override public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) { return client.createWarpTicket(playerUuid, islandId, warpName); }
        @Override public CompletableFuture<RoutePlan> resolveHome(UUID playerUuid) { return createHomeTicket(playerUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisit(UUID visitorUuid, UUID targetIslandId) { return createVisitTicket(visitorUuid, targetIslandId).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveRandomVisit(UUID visitorUuid) { return createRandomVisitTicket(visitorUuid).thenApply(PaperCloudIslandsApi::plan); }
    }

    private static final class PermissionService implements IslandPermissionService {
        private final CloudIslandsPaperAgent agent;

        private PermissionService(CloudIslandsPaperAgent agent) {
            this.agent = agent;
        }

        @Override
        public CompletableFuture<PermissionResult> check(UUID playerUuid, UUID islandId, IslandPermission permission) {
            boolean allowed = agent.permissionCache().allowed(islandId, playerUuid, permission, false);
            return CompletableFuture.completedFuture(allowed ? PermissionResult.allow(IslandRole.MEMBER) : PermissionResult.deny("DEFAULT_DENY", IslandRole.VISITOR));
        }

        @Override
        public CompletableFuture<PermissionResult> checkAt(UUID playerUuid, String worldName, int blockX, int blockY, int blockZ, IslandPermission permission) {
            return CompletableFuture.completedFuture(agent.protection().checkBlock(playerUuid, worldName, blockX, blockY, blockZ, permission));
        }
    }

    private static final class RuntimeService implements IslandRuntimeService {
        private final CoreApiClient client;

        private RuntimeService(CoreApiClient client) {
            this.client = client;
        }

        @Override
        public CompletableFuture<IslandRuntimeSnapshot> activate(UUID islandId, String preferredPool) {
            return client.activateIsland(islandId).thenCompose(_body -> client.adminIslandWhere(islandId)).thenApply(PaperCloudIslandsApi::runtime);
        }

        @Override
        public CompletableFuture<Void> deactivate(UUID islandId) {
            return client.deactivateIsland(islandId).thenApply(_body -> null);
        }

        @Override
        public CompletableFuture<Void> heartbeat(String nodeId, NodeHeartbeat heartbeat) {
            return client.publishHeartbeat(new NodeHeartbeatRequest(
                nodeId,
                "island",
                nodeId,
                "paper-api",
                NodeState.READY,
                heartbeat.players(),
                heartbeat.activeIslands(),
                heartbeat.mspt(),
                heartbeat.activationQueue(),
                heartbeat.heapUsedMb(),
                heartbeat.heapMaxMb(),
                true,
                "*"
            ));
        }
    }

    private static final class AdminService implements IslandAdminService {
        private final CoreApiClient client;

        private AdminService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<Void> drainNode(String nodeId) { return client.drainNode(nodeId).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> undrainNode(String nodeId) { return client.undrainNode(nodeId).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> migrateIsland(UUID islandId, String targetNode) { return client.migrateIsland(islandId, targetNode).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> snapshotIsland(UUID islandId, String reason) { return client.requestIslandSnapshot(islandId, reason); }
        @Override public CompletableFuture<Void> restoreIsland(UUID islandId, long snapshotNo) { return client.restoreIslandSnapshot(islandId, snapshotNo); }
        @Override public CompletableFuture<Void> quarantineIsland(UUID islandId, String reason) { return client.quarantineIsland(islandId, reason).thenApply(_body -> null); }

        @Override
        public CompletableFuture<List<String>> listNodes() {
            return client.listNodes().thenApply(PaperCloudIslandsApi::nodeIds);
        }
    }

    private static final class CommandService implements IslandCommandService {
        private final CoreApiClient client;

        private CommandService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid, String templateId) { return client.createIsland(ownerUuid, templateId); }
        @Override public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) { return client.deleteIsland(requesterUuid, islandId); }
        @Override public CompletableFuture<Void> invite(UUID islandId, UUID inviterUuid, UUID targetUuid) { return client.createIslandInvite(islandId, inviterUuid, targetUuid).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> acceptInvite(UUID inviteId, UUID playerUuid) { return client.acceptIslandInvite(inviteId, playerUuid); }
        @Override public CompletableFuture<Void> declineInvite(UUID inviteId, UUID playerUuid) { return client.declineIslandInvite(inviteId, playerUuid); }
        @Override public CompletableFuture<Void> kick(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.removeIslandMember(islandId, actorUuid, targetUuid); }
        @Override public CompletableFuture<Void> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, IslandRole role) { return client.setIslandMember(islandId, actorUuid, targetUuid, role); }
        @Override public CompletableFuture<Void> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.transferIslandOwnership(islandId, actorUuid, targetUuid); }
        @Override public CompletableFuture<Void> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) { return client.setIslandFlag(islandId, actorUuid, flag, value); }
        @Override public CompletableFuture<Void> setPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) { return client.setIslandPermission(islandId, actorUuid, role, permission, allowed); }
        @Override public CompletableFuture<Void> setLocked(UUID islandId, UUID actorUuid, boolean locked) { return client.setIslandLocked(islandId, actorUuid, locked); }
        @Override public CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return client.setIslandHome(islandId, actorUuid, name, location); }
        @Override public CompletableFuture<Void> setBiome(UUID islandId, UUID actorUuid, String biomeKey) { return client.setIslandBiome(islandId, actorUuid, biomeKey); }
        @Override public CompletableFuture<Void> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value) { return client.setIslandLimit(islandId, actorUuid, limitKey, value).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> createWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return client.setIslandWarp(islandId, actorUuid, name, location, false); }
        @Override public CompletableFuture<Void> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) { return client.purchaseIslandUpgrade(islandId, actorUuid, upgradeKey).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey) { return client.completeIslandMission(islandId, actorUuid, missionKey).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> sendChat(UUID islandId, UUID actorUuid, String channel, String message) { return client.sendIslandChat(islandId, actorUuid, channel, message).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> depositBank(UUID islandId, UUID actorUuid, BigDecimal amount) { return client.depositIslandBank(islandId, actorUuid, amount.toPlainString()).thenApply(_body -> null); }
        @Override public CompletableFuture<Void> withdrawBank(UUID islandId, UUID actorUuid, BigDecimal amount) { return client.withdrawIslandBank(islandId, actorUuid, amount.toPlainString()).thenApply(_body -> null); }
    }

    private static Optional<IslandSnapshot> island(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        return Optional.of(new IslandSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            uuid(json, "ownerUuid", new UUID(0L, 0L)),
            text(json, "name", ""),
            enumValue(IslandState.class, text(json, "state", "INACTIVE_READY"), IslandState.INACTIVE_READY),
            integer(json, "size", 0),
            longValue(json, "level", 0L),
            text(json, "worth", "0"),
            bool(json, "publicAccess", false),
            instant(text(json, "createdAt", Instant.EPOCH.toString())),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        ));
    }

    private static IslandRuntimeSnapshot runtime(String json) {
        return new IslandRuntimeSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            enumValue(IslandState.class, text(json, "state", "RECOVERY_REQUIRED"), IslandState.RECOVERY_REQUIRED),
            nullableText(json, "activeNode"),
            nullableText(json, "activeWorld"),
            nullableInteger(json, "cellX"),
            nullableInteger(json, "cellZ"),
            nullableText(json, "leaseOwner"),
            longValue(json, "fencingToken", 0L),
            nullableInstant(json, "activatedAt"),
            nullableInstant(json, "lastHeartbeat")
        );
    }

    private static RoutePlan plan(RouteTicket ticket) {
        return new RoutePlan(ticket.islandId(), ticket.targetNode(), ticket.payload().getOrDefault("targetServerName", ticket.targetNode()), ticket.action(), ticket.state() == RouteTicketState.PREPARING);
    }

    private static List<IslandMemberSnapshot> members(String json) {
        List<IslandMemberSnapshot> members = new ArrayList<>();
        for (String object : objects(json, "members")) {
            members.add(new IslandMemberSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                uuid(object, "playerUuid", new UUID(0L, 0L)),
                enumValue(IslandRole.class, text(object, "role", "VISITOR"), IslandRole.VISITOR),
                instant(text(object, "joinedAt", Instant.EPOCH.toString()))
            ));
        }
        return members;
    }

    private static List<IslandBanSnapshot> bans(String json) {
        List<IslandBanSnapshot> bans = new ArrayList<>();
        for (String object : objects(json, "bans")) {
            bans.add(new IslandBanSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                uuid(object, "bannedUuid", new UUID(0L, 0L)),
                uuid(object, "actorUuid", new UUID(0L, 0L)),
                text(object, "reason", ""),
                instant(text(object, "createdAt", Instant.EPOCH.toString())),
                nullableInstant(object, "expiresAt")
            ));
        }
        return bans;
    }

    private static List<IslandInviteSnapshot> invites(String json) {
        List<IslandInviteSnapshot> invites = new ArrayList<>();
        for (String object : objects(json, "invites")) {
            invites.add(new IslandInviteSnapshot(
                uuid(object, "inviteId", new UUID(0L, 0L)),
                uuid(object, "islandId", new UUID(0L, 0L)),
                uuid(object, "inviterUuid", new UUID(0L, 0L)),
                uuid(object, "targetUuid", new UUID(0L, 0L)),
                text(object, "state", "PENDING"),
                instant(text(object, "createdAt", Instant.EPOCH.toString())),
                instant(text(object, "expiresAt", Instant.EPOCH.toString()))
            ));
        }
        return invites;
    }

    private static List<IslandHomeSnapshot> homes(String json) {
        List<IslandHomeSnapshot> homes = new ArrayList<>();
        for (String object : objects(json, "homes")) {
            homes.add(new IslandHomeSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                text(object, "name", "default"),
                location(object),
                uuid(object, "createdBy", new UUID(0L, 0L)),
                instant(text(object, "createdAt", Instant.EPOCH.toString()))
            ));
        }
        return homes;
    }

    private static List<IslandWarpSnapshot> warps(String json) {
        List<IslandWarpSnapshot> warps = new ArrayList<>();
        for (String object : objects(json, "warps")) {
            warps.add(new IslandWarpSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                text(object, "name", "default"),
                location(object),
                bool(object, "publicAccess", false),
                uuid(object, "createdBy", new UUID(0L, 0L)),
                instant(text(object, "createdAt", Instant.EPOCH.toString()))
            ));
        }
        return warps;
    }

    private static List<IslandPermissionRuleSnapshot> permissionRules(String json) {
        List<IslandPermissionRuleSnapshot> rules = new ArrayList<>();
        for (String object : objects(json, "rules")) {
            rules.add(new IslandPermissionRuleSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                enumValue(IslandRole.class, text(object, "role", "VISITOR"), IslandRole.VISITOR),
                enumValue(IslandPermission.class, text(object, "permission", "INTERACT"), IslandPermission.INTERACT),
                bool(object, "allowed", false)
            ));
        }
        return rules;
    }

    private static IslandBiomeSnapshot biome(String json) {
        return new IslandBiomeSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "biomeKey", "minecraft:plains"),
            uuid(json, "updatedBy", new UUID(0L, 0L)),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        );
    }

    private static IslandFlagsSnapshot flags(String json) {
        Map<IslandFlag, String> values = new EnumMap<>(IslandFlag.class);
        for (IslandFlag flag : IslandFlag.values()) {
            String value = text(json, flag.name(), null);
            if (value != null) {
                values.put(flag, value);
            }
        }
        return new IslandFlagsSnapshot(uuid(json, "islandId", new UUID(0L, 0L)), Map.copyOf(values));
    }

    private static List<IslandLimitSnapshot> limits(String json) {
        List<IslandLimitSnapshot> limits = new ArrayList<>();
        for (String object : objects(json, "limits")) {
            limits.add(new IslandLimitSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                text(object, "limitKey", ""),
                longValue(object, "value", 0L),
                uuid(object, "updatedBy", new UUID(0L, 0L)),
                instant(text(object, "updatedAt", Instant.EPOCH.toString()))
            ));
        }
        return limits;
    }

    private static IslandBankSnapshot bank(String json) {
        return new IslandBankSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "balance", "0"),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        );
    }

    private static IslandLevelSnapshot level(String json) {
        return new IslandLevelSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            longValue(json, "level", 0L),
            text(json, "worth", "0"),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        );
    }

    private static List<IslandUpgradeSnapshot> upgrades(String json) {
        List<IslandUpgradeSnapshot> upgrades = new ArrayList<>();
        for (String object : objects(json, "upgrades")) {
            upgrades.add(new IslandUpgradeSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                text(object, "upgradeKey", ""),
                enumValue(UpgradeType.class, text(object, "type", "ISLAND_SIZE"), UpgradeType.ISLAND_SIZE),
                integer(object, "level", 0),
                instant(text(object, "updatedAt", Instant.EPOCH.toString()))
            ));
        }
        return upgrades;
    }

    private static List<IslandMissionSnapshot> missions(String json) {
        List<IslandMissionSnapshot> missions = new ArrayList<>();
        for (String object : objects(json, "missions")) {
            missions.add(new IslandMissionSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                text(object, "missionKey", ""),
                text(object, "kind", "MISSION"),
                text(object, "title", ""),
                longValue(object, "progress", 0L),
                longValue(object, "goal", 0L),
                bool(object, "completed", false),
                text(object, "reward", ""),
                instant(text(object, "updatedAt", Instant.EPOCH.toString()))
            ));
        }
        return missions;
    }

    private static List<IslandSnapshotRecord> snapshots(String json) {
        List<IslandSnapshotRecord> snapshots = new ArrayList<>();
        for (String object : objects(json, "snapshots")) {
            snapshots.add(new IslandSnapshotRecord(
                uuid(object, "snapshotId", new UUID(0L, 0L)),
                uuid(object, "islandId", new UUID(0L, 0L)),
                longValue(object, "snapshotNo", 0L),
                text(object, "storagePath", ""),
                text(object, "reason", ""),
                uuid(object, "createdBy", new UUID(0L, 0L)),
                text(object, "checksum", ""),
                longValue(object, "sizeBytes", 0L),
                instant(text(object, "createdAt", Instant.EPOCH.toString()))
            ));
        }
        return snapshots;
    }

    private static List<IslandLogRecord> logs(String json) {
        List<IslandLogRecord> logs = new ArrayList<>();
        for (String object : objects(json, "logs")) {
            logs.add(new IslandLogRecord(
                uuid(object, "logId", new UUID(0L, 0L)),
                uuid(object, "islandId", new UUID(0L, 0L)),
                uuid(object, "actorUuid", new UUID(0L, 0L)),
                text(object, "action", ""),
                stringMap(object, "payload"),
                instant(text(object, "createdAt", Instant.EPOCH.toString()))
            ));
        }
        return logs;
    }

    private static IslandLocation location(String json) {
        return new IslandLocation(
            text(json, "worldName", ""),
            decimal(json, "localX", 0.5D),
            decimal(json, "localY", 100.0D),
            decimal(json, "localZ", 0.5D),
            (float) decimal(json, "yaw", 0.0D),
            (float) decimal(json, "pitch", 0.0D)
        );
    }

    private static List<String> nodeIds(String json) {
        List<String> ids = new ArrayList<>();
        String needle = "\"nodeId\":\"";
        int index = 0;
        while ((index = json.indexOf(needle, index)) >= 0) {
            int start = index + needle.length();
            int end = json.indexOf('"', start);
            if (end < 0) {
                break;
            }
            ids.add(json.substring(start, end));
            index = end + 1;
        }
        return ids;
    }

    private static List<String> objects(String json, String arrayField) {
        List<String> values = new ArrayList<>();
        String needle = "\"" + arrayField + "\":[";
        int arrayStart = json.indexOf(needle);
        if (arrayStart < 0) {
            return values;
        }
        int index = arrayStart + needle.length();
        int depth = 0;
        int objectStart = -1;
        while (index < json.length()) {
            char ch = json.charAt(index);
            if (ch == '{') {
                if (depth == 0) {
                    objectStart = index;
                }
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && objectStart >= 0) {
                    values.add(json.substring(objectStart, index + 1));
                    objectStart = -1;
                }
            } else if (ch == ']' && depth == 0) {
                break;
            }
            index++;
        }
        return values;
    }

    private static Map<String, String> stringMap(String json, String field) {
        String needle = "\"" + field + "\":{";
        int start = json.indexOf(needle);
        if (start < 0) {
            return Map.of();
        }
        int index = start + needle.length();
        int depth = 1;
        while (index < json.length() && depth > 0) {
            char ch = json.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
            }
            index++;
        }
        if (depth != 0) {
            return Map.of();
        }
        String object = json.substring(start + needle.length(), index - 1);
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        for (String entry : object.split(",")) {
            int separator = entry.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = entry.substring(0, separator).trim().replace("\"", "");
            String value = entry.substring(separator + 1).trim().replace("\"", "");
            values.put(key, value);
        }
        return Map.copyOf(values);
    }

    private static String text(String json, String field, String fallback) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        int end = json.indexOf('"', valueStart);
        return end < 0 ? fallback : json.substring(valueStart, end);
    }

    private static String nullableText(String json, String field) {
        String nullNeedle = "\"" + field + "\":null";
        return json.contains(nullNeedle) ? null : text(json, field, null);
    }

    private static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(String json, String field, int fallback) {
        try {
            return Integer.parseInt(number(json, field, Integer.toString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Integer nullableInteger(String json, String field) {
        return json.contains("\"" + field + "\":null") ? null : integer(json, field, 0);
    }

    private static long longValue(String json, String field, long fallback) {
        try {
            return Long.parseLong(number(json, field, Long.toString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double decimal(String json, String field, double fallback) {
        try {
            return Double.parseDouble(number(json, field, Double.toString(fallback)));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean bool(String json, String field, boolean fallback) {
        String value = number(json, field, Boolean.toString(fallback));
        return Boolean.parseBoolean(value);
    }

    private static String number(String json, String field, String fallback) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return fallback;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length() && ",}".indexOf(json.charAt(end)) < 0) {
            end++;
        }
        return json.substring(valueStart, end).replace("\"", "").trim();
    }

    private static Instant instant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static Instant nullableInstant(String json, String field) {
        String value = nullableText(json, field);
        return value == null ? null : instant(value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

}
