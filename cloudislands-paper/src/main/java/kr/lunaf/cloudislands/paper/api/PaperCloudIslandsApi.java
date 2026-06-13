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
import kr.lunaf.cloudislands.api.model.AuditLogSnapshot;
import kr.lunaf.cloudislands.api.model.BlockValueSnapshot;
import kr.lunaf.cloudislands.api.model.ClaimedIslandJobSnapshot;
import kr.lunaf.cloudislands.api.model.CoreMaintenanceResult;
import kr.lunaf.cloudislands.api.model.GlobalEventBatchSnapshot;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBoundarySnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandChatResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandJobSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLevelSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandNodeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRankSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeJobType;
import kr.lunaf.cloudislands.api.model.IslandSizeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshotRecord;
import kr.lunaf.cloudislands.api.model.IslandState;
import kr.lunaf.cloudislands.api.model.IslandTemplateSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWorthSnapshot;
import kr.lunaf.cloudislands.api.model.JobRecoveryResult;
import kr.lunaf.cloudislands.api.model.MigrationIssueSnapshot;
import kr.lunaf.cloudislands.api.model.MigrationRunSnapshot;
import kr.lunaf.cloudislands.api.model.NodeState;
import kr.lunaf.cloudislands.api.model.NodeLevelScanSnapshot;
import kr.lunaf.cloudislands.api.model.NodeStorageSnapshot;
import kr.lunaf.cloudislands.api.model.NodeSweepResult;
import kr.lunaf.cloudislands.api.model.PermissionResult;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.api.model.PlayerRouteSessionSnapshot;
import kr.lunaf.cloudislands.api.model.RouteAction;
import kr.lunaf.cloudislands.api.model.RouteClearResult;
import kr.lunaf.cloudislands.api.model.RouteDebugSnapshot;
import kr.lunaf.cloudislands.api.model.RoutePlan;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.api.model.RouteTicketState;
import kr.lunaf.cloudislands.api.service.IslandAdminService;
import kr.lunaf.cloudislands.api.service.IslandCommandService;
import kr.lunaf.cloudislands.api.service.IslandEventService;
import kr.lunaf.cloudislands.api.service.IslandPermissionService;
import kr.lunaf.cloudislands.api.service.IslandQueryService;
import kr.lunaf.cloudislands.api.service.IslandRoutingService;
import kr.lunaf.cloudislands.api.service.IslandRuntimeService;
import kr.lunaf.cloudislands.api.service.PlayerIslandService;
import kr.lunaf.cloudislands.api.upgrade.IslandUpgradeSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradePurchaseSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeRuleSnapshot;
import kr.lunaf.cloudislands.api.upgrade.UpgradeType;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.CloudIslandsPaperAgent;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public final class PaperCloudIslandsApi implements CloudIslandsApi {
    private final QueryService query;
    private final PlayerService players;
    private final RoutingService routing;
    private final PermissionService permissions;
    private final RuntimeService runtime;
    private final EventService events;
    private final AdminService admin;
    private final CommandService commands;

    public PaperCloudIslandsApi(CoreApiClient client, CloudIslandsPaperAgent agent) {
        this.query = new QueryService(client);
        this.players = new PlayerService(client, query);
        this.routing = new RoutingService(client);
        this.permissions = new PermissionService(agent);
        this.runtime = new RuntimeService(client);
        this.events = new EventService(client);
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
    public IslandEventService events() {
        return events;
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
        public CompletableFuture<List<IslandWarpSnapshot>> getPublicWarps(int limit) {
            return client.listPublicWarps(limit).thenApply(PaperCloudIslandsApi::warps);
        }

        @Override
        public CompletableFuture<List<IslandPermissionRuleSnapshot>> getPermissionRules(UUID islandId) {
            return client.listIslandPermissions(islandId).thenApply(PaperCloudIslandsApi::permissionRules);
        }

        @Override
        public CompletableFuture<List<IslandRoleSnapshot>> getRoles(UUID islandId) {
            return client.listIslandRoles(islandId).thenApply(PaperCloudIslandsApi::roles);
        }

        @Override
        public CompletableFuture<IslandBoundarySnapshot> getBoundary(UUID islandId) {
            return client.islandInfo(islandId).thenApply(PaperCloudIslandsApi::boundary);
        }

        @Override
        public CompletableFuture<IslandSizeSnapshot> getSize(UUID islandId) {
            return client.islandInfo(islandId).thenApply(PaperCloudIslandsApi::size);
        }

        @Override
        public CompletableFuture<IslandWorthSnapshot> getWorth(UUID islandId) {
            return client.islandInfo(islandId).thenApply(PaperCloudIslandsApi::worth);
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
        public CompletableFuture<List<IslandRankSnapshot>> getTopByLevel(int limit) {
            return client.topIslandsByLevel(limit).thenApply(PaperCloudIslandsApi::rankings);
        }

        @Override
        public CompletableFuture<List<IslandRankSnapshot>> getTopByWorth(int limit) {
            return client.topIslandsByWorth(limit).thenApply(PaperCloudIslandsApi::rankings);
        }

        @Override
        public CompletableFuture<List<IslandSnapshot>> getPublicIslands(int limit) {
            return client.listPublicIslands(limit).thenApply(PaperCloudIslandsApi::islands);
        }

        @Override
        public CompletableFuture<List<IslandUpgradeSnapshot>> getUpgrades(UUID islandId) {
            return client.listIslandUpgrades(islandId).thenApply(PaperCloudIslandsApi::upgrades);
        }

        @Override
        public CompletableFuture<List<UpgradeRuleSnapshot>> getUpgradeRules() {
            return client.listUpgradeRules().thenApply(PaperCloudIslandsApi::upgradeRules);
        }

        @Override
        public CompletableFuture<List<BlockValueSnapshot>> getBlockValues() {
            return client.listBlockValues().thenApply(PaperCloudIslandsApi::blockValues);
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
        private final CoreApiClient client;
        private final QueryService query;

        private PlayerService(CoreApiClient client, QueryService query) {
            this.client = client;
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
            return client.listPlayerIslands(playerUuid).thenApply(PaperCloudIslandsApi::islands);
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> getProfile(UUID playerUuid) {
            return client.playerInfo(playerUuid).thenApply(PaperCloudIslandsApi::playerProfile);
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> setPrimaryIsland(UUID playerUuid, UUID islandId) {
            return client.setPlayerIsland(playerUuid, islandId).thenApply(PaperCloudIslandsApi::playerProfile);
        }

        @Override
        public CompletableFuture<Optional<PlayerIslandProfile>> clearPrimaryIsland(UUID playerUuid) {
            return client.clearPlayerIsland(playerUuid).thenApply(PaperCloudIslandsApi::playerProfile);
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
        @Override public CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName) { return client.createVisitTicket(visitorUuid, islandName); }
        @Override public CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid) { return client.createVisitTicketForOwner(visitorUuid, ownerUuid); }
        @Override public CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid) { return client.createRandomVisitTicket(visitorUuid); }
        @Override public CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName) { return client.createWarpTicket(playerUuid, islandId, warpName); }
        @Override public CompletableFuture<Void> publishRouteSession(RouteTicket ticket) { return publishRouteSessionResult(ticket).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> publishRouteSessionResult(RouteTicket ticket) { return client.publishRouteSessionResult(ticket).thenApply(body -> action(body, "ROUTE_SESSION_PUBLISHED")); }
        @Override public CompletableFuture<Optional<PlayerRouteSessionSnapshot>> consumeRouteSession(UUID playerUuid, String nodeId) { return client.consumeRouteSession(playerUuid, nodeId).thenApply(session -> session.map(PaperCloudIslandsApi::routeSession)); }
        @Override public CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce) { return client.routeTicketStatus(ticketId, playerUuid, nonce); }
        @Override public CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce) { return client.consumeTicket(ticketId, playerUuid, nodeId, nonce); }
        @Override public CompletableFuture<RoutePlan> resolveHome(UUID playerUuid) { return createHomeTicket(playerUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveHome(UUID playerUuid, String homeName) { return createHomeTicket(playerUuid, homeName).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisit(UUID visitorUuid, UUID targetIslandId) { return createVisitTicket(visitorUuid, targetIslandId).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisitByName(UUID visitorUuid, String islandName) { return createVisitTicket(visitorUuid, islandName).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveVisitByOwner(UUID visitorUuid, UUID ownerUuid) { return createVisitTicketForOwner(visitorUuid, ownerUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveRandomVisit(UUID visitorUuid) { return createRandomVisitTicket(visitorUuid).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<RoutePlan> resolveWarp(UUID playerUuid, UUID islandId, String warpName) { return createWarpTicket(playerUuid, islandId, warpName).thenApply(PaperCloudIslandsApi::plan); }
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
        public CompletableFuture<IslandActionResult> activateResult(UUID islandId, String preferredPool) {
            return client.activateIslandResult(islandId).thenApply(body -> actionCode(body, "ACTIVATED"));
        }

        @Override
        public CompletableFuture<Void> deactivate(UUID islandId) {
            return deactivateResult(islandId).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> deactivateResult(UUID islandId) {
            return client.deactivateIslandResult(islandId).thenApply(body -> actionCode(body, "DEACTIVATED"));
        }

        @Override
        public CompletableFuture<Void> heartbeat(String nodeId, NodeHeartbeat heartbeat) {
            return heartbeatResult(nodeId, heartbeat).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> heartbeatResult(String nodeId, NodeHeartbeat heartbeat) {
            return client.publishHeartbeatResult(new NodeHeartbeatRequest(
                NodeHeartbeatRequest.CURRENT_PROTOCOL_VERSION,
                nodeId,
                "island",
                nodeId,
                "paper-api",
                NodeState.READY,
                heartbeat.players(),
                90,
                110,
                20,
                heartbeat.activeIslands(),
                600,
                heartbeat.mspt(),
                heartbeat.activationQueue(),
                20,
                0.0D,
                heartbeat.heapUsedMb(),
                heartbeat.heapMaxMb(),
                0,
                true,
                "*"
            )).thenApply(body -> action(body, "HEARTBEAT_ACCEPTED"));
        }

        @Override
        public CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta) {
            return recordBlockDeltaResult(islandId, materialKey, delta).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> recordBlockDeltaResult(UUID islandId, String materialKey, long delta) {
            return client.recordBlockDeltaResult(islandId, materialKey, delta).thenApply(body -> action(body, "BLOCK_DELTA_RECORDED"));
        }

        @Override
        public CompletableFuture<List<ClaimedIslandJobSnapshot>> claimJobs(String nodeId, List<String> supportedTypes, int maxJobs) {
            return client.claimJobs(nodeId, jobTypes(supportedTypes), maxJobs).thenApply(jobs -> jobs.stream().map(PaperCloudIslandsApi::claimedJob).toList());
        }

        @Override
        public CompletableFuture<List<ClaimedIslandJobSnapshot>> claimTypedJobs(String nodeId, List<IslandRuntimeJobType> supportedTypes, int maxJobs) {
            return client.claimJobs(nodeId, runtimeJobTypes(supportedTypes), maxJobs).thenApply(jobs -> jobs.stream().map(PaperCloudIslandsApi::claimedJob).toList());
        }

        @Override
        public CompletableFuture<Void> completeJob(String nodeId, UUID jobId) {
            return completeJob(nodeId, jobId, Map.of());
        }

        @Override
        public CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload) {
            return completeJobResult(nodeId, jobId, payload).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload) {
            return client.completeJobResult(nodeId, jobId, payload).thenApply(body -> action(body, "JOB_COMPLETED"));
        }

        @Override
        public CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage) {
            return failJobResult(nodeId, jobId, errorMessage).thenApply(_result -> null);
        }

        @Override
        public CompletableFuture<IslandActionResult> failJobResult(String nodeId, UUID jobId, String errorMessage) {
            return client.failJobResult(nodeId, jobId, errorMessage).thenApply(body -> action(body, "JOB_FAILED"));
        }
    }

    private static List<IslandJobType> jobTypes(List<String> supportedTypes) {
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            return List.of(IslandJobType.CREATE_ISLAND, IslandJobType.ACTIVATE_ISLAND, IslandJobType.SAVE_ISLAND, IslandJobType.DEACTIVATE_ISLAND, IslandJobType.SNAPSHOT_ISLAND, IslandJobType.DELETE_ISLAND, IslandJobType.MIGRATE_ISLAND, IslandJobType.RESTORE_ISLAND, IslandJobType.RESET_ISLAND);
        }
        List<IslandJobType> types = new ArrayList<>();
        for (String supportedType : supportedTypes) {
            try {
                types.add(IslandJobType.valueOf(supportedType.trim().toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // Ignore unknown worker capabilities from external API callers.
            }
        }
        return types;
    }

    private static List<IslandJobType> runtimeJobTypes(List<IslandRuntimeJobType> supportedTypes) {
        if (supportedTypes == null || supportedTypes.isEmpty()) {
            return jobTypes(List.of());
        }
        return supportedTypes.stream().map(type -> IslandJobType.valueOf(type.name())).toList();
    }

    private static ClaimedIslandJobSnapshot claimedJob(IslandJob job) {
        return new ClaimedIslandJobSnapshot(
            job.jobId(),
            job.type().name(),
            job.islandId(),
            job.targetNode(),
            job.priority(),
            job.payload(),
            job.createdAt()
        );
    }

    private static final class AdminService implements IslandAdminService {
        private final CoreApiClient client;

        private AdminService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<Void> drainNode(String nodeId) { return drainNodeResult(nodeId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> drainNodeResult(String nodeId) { return client.drainNodeResult(nodeId).thenApply(body -> action(body, "NODE_DRAINED")); }
        @Override public CompletableFuture<Void> undrainNode(String nodeId) { return undrainNodeResult(nodeId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> undrainNodeResult(String nodeId) { return client.undrainNodeResult(nodeId).thenApply(body -> action(body, "NODE_UNDRAINED")); }
        @Override public CompletableFuture<Void> sweepNode(String nodeId) { return sweepNodeResult(nodeId).thenApply(_result -> null); }
        @Override public CompletableFuture<NodeSweepResult> sweepNodeResult(String nodeId) { return client.sweepNodeResult(nodeId).thenApply(PaperCloudIslandsApi::nodeSweep); }
        @Override public CompletableFuture<Void> kickAllNode(String nodeId, String reason) { return kickAllNodeResult(nodeId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> kickAllNodeResult(String nodeId, String reason) { return client.kickAllNodeResult(nodeId, reason).thenApply(body -> action(body, "NODE_KICKALL_REQUESTED")); }
        @Override public CompletableFuture<Void> shutdownNodeSafely(String nodeId, String reason) { return shutdownNodeSafelyResult(nodeId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> shutdownNodeSafelyResult(String nodeId, String reason) { return client.shutdownNodeSafelyResult(nodeId, reason).thenApply(body -> action(body, "NODE_SHUTDOWN_SAFE_REQUESTED")); }
        @Override public CompletableFuture<Void> activateIsland(UUID islandId) { return activateIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> activateIslandResult(UUID islandId) { return client.activateIslandResult(islandId).thenApply(body -> actionCode(body, "ACTIVATE_REQUESTED")); }
        @Override public CompletableFuture<Void> deactivateIsland(UUID islandId) { return deactivateIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> deactivateIslandResult(UUID islandId) { return client.deactivateIslandResult(islandId).thenApply(body -> actionCode(body, "DEACTIVATE_REQUESTED")); }
        @Override public CompletableFuture<Void> migrateIsland(UUID islandId, String targetNode) { return migrateIslandResult(islandId, targetNode).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> migrateIslandResult(UUID islandId, String targetNode) { return client.migrateIslandResult(islandId, targetNode).thenApply(body -> actionCode(body, "MIGRATED")); }
        @Override public CompletableFuture<Void> saveIsland(UUID islandId) { return saveIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> saveIslandResult(UUID islandId) { return client.requestIslandSnapshotResult(islandId, "ADMIN_SAVE").thenApply(body -> actionCode(body, "SAVE_REQUESTED")); }
        @Override public CompletableFuture<Void> snapshotIsland(UUID islandId, String reason) { return snapshotIslandResult(islandId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> snapshotIslandResult(UUID islandId, String reason) { return client.requestIslandSnapshotResult(islandId, reason).thenApply(body -> actionCode(body, "SNAPSHOT_REQUESTED")); }
        @Override public CompletableFuture<List<IslandSnapshotRecord>> listIslandSnapshots(UUID islandId, int limit) { return client.listIslandSnapshots(islandId, limit).thenApply(PaperCloudIslandsApi::snapshots); }
        @Override public CompletableFuture<Void> restoreIsland(UUID islandId, long snapshotNo) { return restoreIslandResult(islandId, snapshotNo).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> restoreIslandResult(UUID islandId, long snapshotNo) { return client.restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionCode(body, "RESTORE_REQUESTED")); }
        @Override public CompletableFuture<Void> rollbackIsland(UUID islandId, long snapshotNo) { return rollbackIslandResult(islandId, snapshotNo).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> rollbackIslandResult(UUID islandId, long snapshotNo) { return client.restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionCode(body, "ROLLBACK_REQUESTED")); }
        @Override public CompletableFuture<Void> quarantineIsland(UUID islandId, String reason) { return quarantineIslandResult(islandId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> quarantineIslandResult(UUID islandId, String reason) { return client.quarantineIslandResult(islandId, reason).thenApply(body -> actionCode(body, "QUARANTINED")); }
        @Override public CompletableFuture<Void> repairIsland(UUID islandId, String reason) { return repairIslandResult(islandId, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<Optional<IslandRuntimeSnapshot>> repairIslandResult(UUID islandId, String reason) { return client.repairIslandResult(islandId, reason).thenApply(PaperCloudIslandsApi::runtimeOptional); }
        @Override public CompletableFuture<Void> deleteIsland(UUID islandId) { return adminDeleteIslandResult(islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> adminDeleteIslandResult(UUID islandId) { return client.adminDeleteIslandResult(islandId).thenApply(body -> action(body, "ISLAND_DELETED")); }
        @Override public CompletableFuture<RouteTicket> createAdminTeleportTicket(UUID playerUuid, UUID islandId) { return client.adminIslandTeleport(playerUuid, islandId); }
        @Override public CompletableFuture<RoutePlan> resolveAdminTeleport(UUID playerUuid, UUID islandId) { return createAdminTeleportTicket(playerUuid, islandId).thenApply(PaperCloudIslandsApi::plan); }
        @Override public CompletableFuture<Optional<RouteTicket>> getRouteTicket(UUID ticketId) { return client.routeTicket(ticketId).thenApply(PaperCloudIslandsApi::routeTicket); }
        @Override public CompletableFuture<Optional<PlayerRouteSessionSnapshot>> getRouteSession(UUID playerUuid) { return client.debugRoutes(playerUuid).thenApply(PaperCloudIslandsApi::routeSession); }
        @Override public CompletableFuture<RouteDebugSnapshot> getRouteDebug() { return client.debugRoutes(new UUID(0L, 0L)).thenApply(PaperCloudIslandsApi::routeDebug); }
        @Override public CompletableFuture<Void> clearRoute(UUID playerUuid, UUID ticketId) { return clearRouteResult(playerUuid, ticketId).thenApply(_result -> null); }
        @Override public CompletableFuture<RouteClearResult> clearRouteResult(UUID playerUuid, UUID ticketId) { return client.clearRouteResult(playerUuid, ticketId).thenApply(PaperCloudIslandsApi::routeClear); }
        @Override public CompletableFuture<List<IslandJobSnapshot>> listJobs() { return client.listJobs().thenApply(PaperCloudIslandsApi::jobs); }
        @Override public CompletableFuture<Void> retryJob(UUID jobId) { return retryJobResult(jobId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> retryJobResult(UUID jobId) { return client.retryJobResult(jobId).thenApply(body -> action(body, "JOB_RETRIED")); }
        @Override public CompletableFuture<Void> cancelJob(UUID jobId) { return cancelJobResult(jobId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> cancelJobResult(UUID jobId) { return client.cancelJobResult(jobId).thenApply(body -> action(body, "JOB_CANCELED")); }
        @Override public CompletableFuture<Void> recoverJobs(String nodeId, long minIdleMillis, int maxJobs) { return recoverJobsResult(nodeId, minIdleMillis, maxJobs).thenApply(_result -> null); }
        @Override public CompletableFuture<JobRecoveryResult> recoverJobsResult(String nodeId, long minIdleMillis, int maxJobs) { return client.recoverJobsResult(nodeId, minIdleMillis, maxJobs).thenApply(PaperCloudIslandsApi::jobRecovery); }
        @Override public CompletableFuture<Void> clearCache() { return clearCacheResult().thenApply(_result -> null); }
        @Override public CompletableFuture<CoreMaintenanceResult> clearCacheResult() { return client.clearCacheResult().thenApply(body -> maintenance(body, false)); }
        @Override public CompletableFuture<Void> reload() { return reloadResult().thenApply(_result -> null); }
        @Override public CompletableFuture<CoreMaintenanceResult> reloadResult() { return client.reloadResult().thenApply(body -> maintenance(body, bool(body, "reloaded", false))); }
        @Override public CompletableFuture<Optional<PlayerIslandProfile>> getPlayerProfile(UUID playerUuid) { return client.playerInfo(playerUuid).thenApply(PaperCloudIslandsApi::playerProfile); }
        @Override public CompletableFuture<Optional<PlayerIslandProfile>> setPlayerPrimaryIsland(UUID playerUuid, UUID islandId) { return client.setPlayerIsland(playerUuid, islandId).thenApply(PaperCloudIslandsApi::playerProfile); }
        @Override public CompletableFuture<Optional<PlayerIslandProfile>> clearPlayerPrimaryIsland(UUID playerUuid) { return client.clearPlayerIsland(playerUuid).thenApply(PaperCloudIslandsApi::playerProfile); }
        @Override public CompletableFuture<Void> setBlockValue(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) { return setBlockValueResult(actorUuid, materialKey, worth, levelPoints, limit).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setBlockValueResult(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit) { return client.setBlockValueResult(actorUuid, materialKey, worth, levelPoints, limit).thenApply(body -> action(body, "BLOCK_VALUE_SET")); }
        @Override public CompletableFuture<List<GlobalEventSnapshot>> listEvents() { return client.listEvents().thenApply(PaperCloudIslandsApi::events); }
        @Override public CompletableFuture<List<GlobalEventSnapshot>> listEvents(int limit) { return client.listEvents(limit).thenApply(PaperCloudIslandsApi::events); }
        @Override public CompletableFuture<GlobalEventBatchSnapshot> listEventBatch() { return client.listEvents().thenApply(PaperCloudIslandsApi::eventBatch); }
        @Override public CompletableFuture<GlobalEventBatchSnapshot> listEventBatch(int limit) { return client.listEvents(limit).thenApply(PaperCloudIslandsApi::eventBatch); }
        @Override public CompletableFuture<GlobalEventBatchSnapshot> listEventBatchSince(long sinceSeq, int limit) { return client.listEventsSince(sinceSeq, limit).thenApply(PaperCloudIslandsApi::eventBatch); }
        @Override public CompletableFuture<List<AuditLogSnapshot>> listAuditLogs() { return client.listAuditLogs().thenApply(PaperCloudIslandsApi::auditLogs); }

        @Override
        public CompletableFuture<List<String>> listNodes() {
            return client.listNodes().thenApply(PaperCloudIslandsApi::nodeIds);
        }

        @Override
        public CompletableFuture<List<IslandNodeSnapshot>> listNodeSnapshots() {
            return client.listNodes().thenApply(PaperCloudIslandsApi::nodes);
        }

        @Override
        public CompletableFuture<Optional<IslandNodeSnapshot>> getNodeSnapshot(String nodeId) {
            return client.nodeInfo(nodeId).thenApply(PaperCloudIslandsApi::node);
        }

        @Override
        public CompletableFuture<List<IslandRuntimeSnapshot>> listNodeIslands(String nodeId, int limit) {
            return client.nodeIslands(nodeId, limit).thenApply(PaperCloudIslandsApi::nodeIslands);
        }

        @Override
        public CompletableFuture<List<IslandTemplateSnapshot>> listTemplates() {
            return client.listTemplates().thenApply(PaperCloudIslandsApi::templates);
        }

        @Override
        public CompletableFuture<IslandTemplateSnapshot> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion) {
            return client.upsertTemplate(templateId, displayName, enabled, minNodeVersion).thenApply(PaperCloudIslandsApi::template);
        }

        @Override
        public CompletableFuture<IslandTemplateSnapshot> enableTemplate(String templateId) {
            return client.enableTemplate(templateId).thenApply(PaperCloudIslandsApi::template);
        }

        @Override
        public CompletableFuture<IslandTemplateSnapshot> disableTemplate(String templateId) {
            return client.disableTemplate(templateId).thenApply(PaperCloudIslandsApi::template);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> scanSuperiorSkyblock2(String path) {
            return client.migrateSuperiorSkyblock2("scan", path).thenApply(PaperCloudIslandsApi::migrationRun);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> dryRunSuperiorSkyblock2(String path) {
            return client.migrateSuperiorSkyblock2("dryrun", path).thenApply(PaperCloudIslandsApi::migrationRun);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> extractSuperiorSkyblock2(String outputPath) {
            return client.migrateSuperiorSkyblock2("extract", outputPath).thenApply(PaperCloudIslandsApi::migrationRun);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> importSuperiorSkyblock2(String path) {
            return client.migrateSuperiorSkyblock2("import", path).thenApply(PaperCloudIslandsApi::migrationRun);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> verifySuperiorSkyblock2(String path) {
            return client.migrateSuperiorSkyblock2("verify", path).thenApply(PaperCloudIslandsApi::migrationRun);
        }

        @Override
        public CompletableFuture<MigrationRunSnapshot> rollbackSuperiorSkyblock2(String path) {
            return client.migrateSuperiorSkyblock2("rollback", path).thenApply(PaperCloudIslandsApi::migrationRun);
        }
    }

    private static final class EventService implements IslandEventService {
        private final CoreApiClient client;

        private EventService(CoreApiClient client) {
            this.client = client;
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents() {
            return client.listEvents().thenApply(PaperCloudIslandsApi::events);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents(int limit) {
            return client.listEvents(limit).thenApply(PaperCloudIslandsApi::events);
        }

        @Override
        public CompletableFuture<List<GlobalEventSnapshot>> listGlobalEventsSince(long sinceSeq, int limit) {
            return client.listEventsSince(sinceSeq, limit).thenApply(PaperCloudIslandsApi::events);
        }

        @Override
        public CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch() {
            return client.listEvents().thenApply(PaperCloudIslandsApi::eventBatch);
        }

        @Override
        public CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch(int limit) {
            return client.listEvents(limit).thenApply(PaperCloudIslandsApi::eventBatch);
        }

        @Override
        public CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatchSince(long sinceSeq, int limit) {
            return client.listEventsSince(sinceSeq, limit).thenApply(PaperCloudIslandsApi::eventBatch);
        }
    }

    private static final class CommandService implements IslandCommandService {
        private final CoreApiClient client;

        private CommandService(CoreApiClient client) {
            this.client = client;
        }

        @Override public CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid) { return createIsland(ownerUuid, "default"); }
        @Override public CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid, String templateId) { return client.createIsland(ownerUuid, templateId); }
        @Override public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) { return client.deleteIsland(requesterUuid, islandId); }
        @Override public CompletableFuture<Void> resetIsland(UUID islandId, UUID actorUuid, String reason) { return resetIslandResult(islandId, actorUuid, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> resetIslandResult(UUID islandId, UUID actorUuid, String reason) { return client.resetIslandResult(islandId, actorUuid, reason).thenApply(body -> actionCode(body, "RESET_QUEUED")); }
        @Override public CompletableFuture<Void> invite(UUID islandId, UUID inviterUuid, UUID targetUuid) { return inviteResult(islandId, inviterUuid, targetUuid).thenApply(_invite -> null); }
        @Override public CompletableFuture<IslandInviteSnapshot> inviteResult(UUID islandId, UUID inviterUuid, UUID targetUuid) { return client.createIslandInvite(islandId, inviterUuid, targetUuid).thenApply(PaperCloudIslandsApi::invite); }
        @Override public CompletableFuture<Void> acceptInvite(UUID inviteId, UUID playerUuid) { return acceptInviteResult(inviteId, playerUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> acceptInviteResult(UUID inviteId, UUID playerUuid) { return client.acceptIslandInviteResult(inviteId, playerUuid).thenApply(body -> inviteAction(body, "ACCEPTED")); }
        @Override public CompletableFuture<Void> declineInvite(UUID inviteId, UUID playerUuid) { return declineInviteResult(inviteId, playerUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> declineInviteResult(UUID inviteId, UUID playerUuid) { return client.declineIslandInviteResult(inviteId, playerUuid).thenApply(body -> inviteAction(body, "DECLINED")); }
        @Override public CompletableFuture<Void> acceptInviteFromIsland(UUID playerUuid, UUID islandId) { return acceptInviteFromIslandResult(playerUuid, islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> acceptInviteFromIslandResult(UUID playerUuid, UUID islandId) { return pendingInvite(playerUuid, invite -> invite.islandId().equals(islandId)).thenCompose(invite -> invite.map(value -> acceptInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> declineInviteFromIsland(UUID playerUuid, UUID islandId) { return declineInviteFromIslandResult(playerUuid, islandId).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> declineInviteFromIslandResult(UUID playerUuid, UUID islandId) { return pendingInvite(playerUuid, invite -> invite.islandId().equals(islandId)).thenCompose(invite -> invite.map(value -> declineInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> acceptInviteFromPlayer(UUID playerUuid, UUID inviterUuid) { return acceptInviteFromPlayerResult(playerUuid, inviterUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> acceptInviteFromPlayerResult(UUID playerUuid, UUID inviterUuid) { return pendingInvite(playerUuid, invite -> invite.inviterUuid().equals(inviterUuid)).thenCompose(invite -> invite.map(value -> acceptInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> declineInviteFromPlayer(UUID playerUuid, UUID inviterUuid) { return declineInviteFromPlayerResult(playerUuid, inviterUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandInviteActionResult> declineInviteFromPlayerResult(UUID playerUuid, UUID inviterUuid) { return pendingInvite(playerUuid, invite -> invite.inviterUuid().equals(inviterUuid)).thenCompose(invite -> invite.map(value -> declineInviteResult(value.inviteId(), playerUuid)).orElseGet(() -> CompletableFuture.completedFuture(new IslandInviteActionResult(false, "INVITE_UNAVAILABLE")))); }
        @Override public CompletableFuture<Void> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) { return banVisitorResult(islandId, actorUuid, targetUuid, reason).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> banVisitorResult(UUID islandId, UUID actorUuid, UUID targetUuid, String reason) { return client.banIslandVisitorResult(islandId, actorUuid, targetUuid, reason).thenApply(body -> action(body, "VISITOR_BANNED")); }
        @Override public CompletableFuture<Void> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid) { return pardonVisitorResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> pardonVisitorResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.pardonIslandVisitorResult(islandId, actorUuid, targetUuid).thenApply(body -> action(body, "VISITOR_PARDONED")); }
        @Override public CompletableFuture<Void> kick(UUID islandId, UUID actorUuid, UUID targetUuid) { return kickResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> kickResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.removeIslandMemberResult(islandId, actorUuid, targetUuid).thenApply(body -> action(body, "MEMBER_REMOVED")); }
        @Override public CompletableFuture<Void> trustPlayer(UUID islandId, UUID actorUuid, UUID targetUuid) { return trustPlayerResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> trustPlayerResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.setIslandMemberResult(islandId, actorUuid, targetUuid, IslandRole.TRUSTED).thenApply(body -> action(body, "PLAYER_TRUSTED")); }
        @Override public CompletableFuture<Void> untrustPlayer(UUID islandId, UUID actorUuid, UUID targetUuid) { return untrustPlayerResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> untrustPlayerResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.setIslandMemberResult(islandId, actorUuid, targetUuid, IslandRole.MEMBER).thenApply(body -> action(body, "PLAYER_UNTRUSTED")); }
        @Override public CompletableFuture<Void> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, IslandRole role) { return setRoleResult(islandId, actorUuid, targetUuid, role).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setRoleResult(UUID islandId, UUID actorUuid, UUID targetUuid, IslandRole role) { return client.setIslandMemberResult(islandId, actorUuid, targetUuid, role).thenApply(body -> action(body, "MEMBER_ROLE_SET")); }
        @Override public CompletableFuture<Void> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid) { return transferOwnershipResult(islandId, actorUuid, targetUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> transferOwnershipResult(UUID islandId, UUID actorUuid, UUID targetUuid) { return client.transferIslandOwnershipResult(islandId, actorUuid, targetUuid).thenApply(body -> action(body, "OWNERSHIP_TRANSFERRED")); }
        @Override public CompletableFuture<Void> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) { return setFlagResult(islandId, actorUuid, flag, value).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value) { return client.setIslandFlagResult(islandId, actorUuid, flag, value).thenApply(body -> action(body, "FLAG_SET")); }
        @Override public CompletableFuture<Void> setPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) { return setPermissionResult(islandId, actorUuid, role, permission, allowed).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setPermissionResult(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) { return client.setIslandPermissionResult(islandId, actorUuid, role, permission, allowed).thenApply(body -> action(body, "PERMISSION_SET")); }
        @Override public CompletableFuture<Void> upsertRole(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName) { return upsertRoleResult(islandId, actorUuid, role, weight, displayName).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandRoleSnapshot> upsertRoleResult(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName) { return client.upsertIslandRole(islandId, actorUuid, role, weight, displayName).thenApply(PaperCloudIslandsApi::role); }
        @Override public CompletableFuture<Void> setLocked(UUID islandId, UUID actorUuid, boolean locked) { return setLockedResult(islandId, actorUuid, locked).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setLockedResult(UUID islandId, UUID actorUuid, boolean locked) { return client.setIslandLockedResult(islandId, actorUuid, locked).thenApply(body -> action(body, locked ? "ISLAND_LOCKED" : "ISLAND_UNLOCKED")); }
        @Override public CompletableFuture<Void> lockIsland(UUID islandId, UUID actorUuid) { return lockIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> lockIslandResult(UUID islandId, UUID actorUuid) { return setLockedResult(islandId, actorUuid, true); }
        @Override public CompletableFuture<Void> unlockIsland(UUID islandId, UUID actorUuid) { return unlockIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> unlockIslandResult(UUID islandId, UUID actorUuid) { return setLockedResult(islandId, actorUuid, false); }
        @Override public CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, IslandLocation location) { return setHomeResult(islandId, actorUuid, location).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setHomeResult(UUID islandId, UUID actorUuid, IslandLocation location) { return setHomeResult(islandId, actorUuid, "default", location); }
        @Override public CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return setHomeResult(islandId, actorUuid, name, location).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setHomeResult(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return client.setIslandHomeResult(islandId, actorUuid, name, location).thenApply(body -> action(body, "HOME_SET")); }
        @Override public CompletableFuture<Void> setBiome(UUID islandId, UUID actorUuid, String biomeKey) { return setBiomeResult(islandId, actorUuid, biomeKey).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setBiomeResult(UUID islandId, UUID actorUuid, String biomeKey) { return client.setIslandBiomeResult(islandId, actorUuid, biomeKey).thenApply(body -> action(body, "BIOME_SET")); }
        @Override public CompletableFuture<Void> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value) { return setLimitResult(islandId, actorUuid, limitKey, value).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandLimitSnapshot> setLimitResult(UUID islandId, UUID actorUuid, String limitKey, long value) { return client.setIslandLimit(islandId, actorUuid, limitKey, value).thenApply(PaperCloudIslandsApi::limit); }
        @Override public CompletableFuture<Void> createWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return createWarpResult(islandId, actorUuid, name, location).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> createWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location) { return setWarpResult(islandId, actorUuid, name, location, false); }
        @Override public CompletableFuture<Void> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) { return setWarpResult(islandId, actorUuid, name, location, publicAccess).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) { return client.setIslandWarpResult(islandId, actorUuid, name, location, publicAccess).thenApply(body -> action(body, "WARP_SET")); }
        @Override public CompletableFuture<Void> deleteWarp(UUID islandId, UUID actorUuid, String name) { return deleteWarpResult(islandId, actorUuid, name).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> deleteWarpResult(UUID islandId, UUID actorUuid, String name) { return client.deleteIslandWarpResult(islandId, actorUuid, name).thenApply(body -> action(body, "WARP_DELETED")); }
        @Override public CompletableFuture<Void> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) { return setWarpPublicAccessResult(islandId, actorUuid, name, publicAccess).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setWarpPublicAccessResult(UUID islandId, UUID actorUuid, String name, boolean publicAccess) { return client.setIslandWarpPublicAccessResult(islandId, actorUuid, name, publicAccess).thenApply(body -> action(body, publicAccess ? "WARP_PUBLIC_ACCESS_ENABLED" : "WARP_PUBLIC_ACCESS_DISABLED")); }
        @Override public CompletableFuture<Void> publishWarp(UUID islandId, UUID actorUuid, String name) { return publishWarpResult(islandId, actorUuid, name).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> publishWarpResult(UUID islandId, UUID actorUuid, String name) { return setWarpPublicAccessResult(islandId, actorUuid, name, true); }
        @Override public CompletableFuture<Void> privatizeWarp(UUID islandId, UUID actorUuid, String name) { return privatizeWarpResult(islandId, actorUuid, name).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> privatizeWarpResult(UUID islandId, UUID actorUuid, String name) { return setWarpPublicAccessResult(islandId, actorUuid, name, false); }
        @Override public CompletableFuture<Void> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) { return setPublicAccessResult(islandId, actorUuid, publicAccess).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> setPublicAccessResult(UUID islandId, UUID actorUuid, boolean publicAccess) { return client.setIslandPublicAccessResult(islandId, actorUuid, publicAccess).thenApply(body -> action(body, publicAccess ? "PUBLIC_ACCESS_ENABLED" : "PUBLIC_ACCESS_DISABLED")); }
        @Override public CompletableFuture<Void> publishIsland(UUID islandId, UUID actorUuid) { return publishIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> publishIslandResult(UUID islandId, UUID actorUuid) { return setPublicAccessResult(islandId, actorUuid, true); }
        @Override public CompletableFuture<Void> privatizeIsland(UUID islandId, UUID actorUuid) { return privatizeIslandResult(islandId, actorUuid).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandActionResult> privatizeIslandResult(UUID islandId, UUID actorUuid) { return setPublicAccessResult(islandId, actorUuid, false); }
        @Override public CompletableFuture<IslandLevelSnapshot> recalculateLevel(UUID islandId, UUID actorUuid) { return client.recalculateIslandLevel(islandId, actorUuid).thenApply(PaperCloudIslandsApi::level); }
        @Override public CompletableFuture<Void> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) { return purchaseUpgradeResult(islandId, actorUuid, upgradeKey).thenApply(_result -> null); }
        @Override public CompletableFuture<UpgradePurchaseSnapshot> purchaseUpgradeResult(UUID islandId, UUID actorUuid, String upgradeKey) { return client.purchaseIslandUpgrade(islandId, actorUuid, upgradeKey).thenApply(PaperCloudIslandsApi::upgradePurchase); }
        @Override public CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey) { return completeMission(islandId, actorUuid, missionKey, "MISSION"); }
        @Override public CompletableFuture<Optional<IslandMissionSnapshot>> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey) { return completeMissionResult(islandId, actorUuid, missionKey, "MISSION"); }
        @Override public CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind) { return completeMissionResult(islandId, actorUuid, missionKey, kind).thenApply(_result -> null); }
        @Override public CompletableFuture<Optional<IslandMissionSnapshot>> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind) { return client.completeIslandMission(islandId, actorUuid, missionKey, kind).thenApply(PaperCloudIslandsApi::mission); }
        @Override public CompletableFuture<Void> sendChat(UUID islandId, UUID actorUuid, String channel, String message) { return sendChatResult(islandId, actorUuid, channel, message).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandChatResult> sendChatResult(UUID islandId, UUID actorUuid, String channel, String message) { return client.sendIslandChat(islandId, actorUuid, channel, message).thenApply(PaperCloudIslandsApi::chatResult); }
        @Override public CompletableFuture<Void> sendIslandChat(UUID islandId, UUID actorUuid, String message) { return sendIslandChatResult(islandId, actorUuid, message).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandChatResult> sendIslandChatResult(UUID islandId, UUID actorUuid, String message) { return sendChatResult(islandId, actorUuid, "ISLAND", message); }
        @Override public CompletableFuture<Void> sendTeamChat(UUID islandId, UUID actorUuid, String message) { return sendTeamChatResult(islandId, actorUuid, message).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandChatResult> sendTeamChatResult(UUID islandId, UUID actorUuid, String message) { return sendChatResult(islandId, actorUuid, "TEAM", message); }
        @Override public CompletableFuture<Void> depositBank(UUID islandId, UUID actorUuid, BigDecimal amount) { return depositBankResult(islandId, actorUuid, amount).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandBankChangeSnapshot> depositBankResult(UUID islandId, UUID actorUuid, BigDecimal amount) { return client.depositIslandBank(islandId, actorUuid, amount.toPlainString()).thenApply(PaperCloudIslandsApi::bankDeposit); }
        @Override public CompletableFuture<Void> withdrawBank(UUID islandId, UUID actorUuid, BigDecimal amount) { return withdrawBankResult(islandId, actorUuid, amount).thenApply(_result -> null); }
        @Override public CompletableFuture<IslandBankChangeSnapshot> withdrawBankResult(UUID islandId, UUID actorUuid, BigDecimal amount) { return client.withdrawIslandBank(islandId, actorUuid, amount.toPlainString()).thenApply(PaperCloudIslandsApi::bankChange); }

        private CompletableFuture<Optional<IslandInviteSnapshot>> pendingInvite(UUID playerUuid, java.util.function.Predicate<IslandInviteSnapshot> predicate) {
            return client.listPendingInvites(playerUuid)
                .thenApply(PaperCloudIslandsApi::invites)
                .thenApply(invites -> invites.stream().filter(predicate).findFirst());
        }
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

    private static List<IslandSnapshot> islands(String json) {
        List<IslandSnapshot> islands = new ArrayList<>();
        for (String object : objects(json, "islands")) {
            island(object).ifPresent(islands::add);
        }
        return islands;
    }

    private static IslandBoundarySnapshot boundary(String json) {
        UUID islandId = uuid(json, "islandId", new UUID(0L, 0L));
        int size = integer(json, "size", 0);
        return new IslandBoundarySnapshot(islandId, size, integer(json, "border", size));
    }

    private static IslandSizeSnapshot size(String json) {
        UUID islandId = uuid(json, "islandId", new UUID(0L, 0L));
        int size = integer(json, "size", 0);
        return new IslandSizeSnapshot(islandId, size, integer(json, "border", size));
    }

    private static IslandWorthSnapshot worth(String json) {
        return new IslandWorthSnapshot(uuid(json, "islandId", new UUID(0L, 0L)), text(json, "worth", "0"));
    }

    private static Optional<PlayerIslandProfile> playerProfile(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        String primaryIslandId = nullableText(json, "primaryIslandId");
        return Optional.of(new PlayerIslandProfile(
            uuid(json, "playerUuid", new UUID(0L, 0L)),
            text(json, "lastName", ""),
            primaryIslandId == null || primaryIslandId.isBlank() ? Optional.empty() : Optional.of(uuid(json, "primaryIslandId", new UUID(0L, 0L))),
            instant(text(json, "lastSeenAt", Instant.EPOCH.toString()))
        ));
    }

    private static PlayerRouteSessionSnapshot routeSession(PlayerRouteSession session) {
        return new PlayerRouteSessionSnapshot(
            session.playerUuid(),
            session.ticketId(),
            session.targetNode(),
            session.targetServerName(),
            session.nonce(),
            session.expiresAt()
        );
    }

    private static Optional<PlayerRouteSessionSnapshot> routeSession(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        return Optional.of(new PlayerRouteSessionSnapshot(
            uuid(json, "playerUuid", new UUID(0L, 0L)),
            uuid(json, "ticketId", new UUID(0L, 0L)),
            text(json, "targetNode", ""),
            text(json, "targetServerName", ""),
            text(json, "nonce", ""),
            instant(text(json, "expiresAt", Instant.EPOCH.toString()))
        ));
    }

    private static RouteDebugSnapshot routeDebug(String json) {
        List<PlayerRouteSessionSnapshot> sessions = new ArrayList<>();
        for (String object : objects(json, "sessions")) {
            routeSession(object).ifPresent(sessions::add);
        }
        List<RouteTicket> tickets = new ArrayList<>();
        for (String object : objects(json, "tickets")) {
            routeTicket(object).ifPresent(tickets::add);
        }
        return new RouteDebugSnapshot(List.copyOf(sessions), List.copyOf(tickets));
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

    private static Optional<IslandRuntimeSnapshot> runtimeOptional(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        return Optional.of(runtime(json));
    }

    private static List<IslandRuntimeSnapshot> nodeIslands(String json) {
        List<IslandRuntimeSnapshot> runtimes = new ArrayList<>();
        for (String object : objects(json, "islands")) {
            runtimes.add(runtime(object));
        }
        return runtimes;
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
            invites.add(invite(object));
        }
        return invites;
    }

    private static IslandInviteSnapshot invite(String json) {
        return new IslandInviteSnapshot(
                uuid(json, "inviteId", new UUID(0L, 0L)),
                uuid(json, "islandId", new UUID(0L, 0L)),
                uuid(json, "inviterUuid", new UUID(0L, 0L)),
                uuid(json, "targetUuid", new UUID(0L, 0L)),
                text(json, "state", "PENDING"),
                instant(text(json, "createdAt", Instant.EPOCH.toString())),
                instant(text(json, "expiresAt", Instant.EPOCH.toString()))
        );
    }

    private static IslandInviteActionResult inviteAction(String json, String successCode) {
        boolean applied = json.contains("\"accepted\":true");
        return new IslandInviteActionResult(applied, applied ? successCode : text(json, "code", "FAILED"));
    }

    private static IslandActionResult action(String json, String successCode) {
        boolean accepted = json.contains("\"accepted\":true");
        return new IslandActionResult(accepted, accepted ? successCode : text(json, "code", "FAILED"));
    }

    private static IslandActionResult actionCode(String json, String fallbackCode) {
        boolean accepted = json.contains("\"accepted\":true");
        return new IslandActionResult(accepted, text(json, "code", accepted ? fallbackCode : "FAILED"));
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

    private static List<IslandRoleSnapshot> roles(String json) {
        List<IslandRoleSnapshot> roles = new ArrayList<>();
        for (String object : objects(json, "roles")) {
            roles.add(role(object));
        }
        return roles;
    }

    private static IslandRoleSnapshot role(String json) {
        return new IslandRoleSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            enumValue(IslandRole.class, text(json, "role", "MEMBER"), IslandRole.MEMBER),
            integer(json, "weight", 0),
            text(json, "displayName", "")
        );
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
            limits.add(limit(object));
        }
        return limits;
    }

    private static IslandLimitSnapshot limit(String json) {
        return new IslandLimitSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "limitKey", ""),
            longValue(json, "value", 0L),
            uuid(json, "updatedBy", new UUID(0L, 0L)),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        );
    }

    private static IslandBankSnapshot bank(String json) {
        return new IslandBankSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "balance", "0"),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        );
    }

    private static IslandBankChangeSnapshot bankDeposit(String json) {
        boolean accepted = !json.contains("\"accepted\":false") && !hasError(json);
        return new IslandBankChangeSnapshot(accepted, accepted ? "DEPOSITED" : text(json, "code", "FAILED"), json == null || json.isBlank() || json.contains("\"bank\":null") ? null : bank(json));
    }

    private static IslandBankChangeSnapshot bankChange(String json) {
        return new IslandBankChangeSnapshot(
            bool(json, "accepted", false),
            text(json, "code", hasError(json) ? "FAILED" : ""),
            json == null || json.isBlank() || json.contains("\"bank\":null") ? null : bank(json)
        );
    }

    private static IslandChatResult chatResult(String json) {
        return new IslandChatResult(
            bool(json, "accepted", false),
            text(json, "channel", ""),
            text(json, "message", "")
        );
    }

    private static boolean hasError(String json) {
        return json == null || json.isBlank() || json.contains("\"error\"");
    }

    private static IslandLevelSnapshot level(String json) {
        String calculatedAt = text(json, "calculatedAt", text(json, "updatedAt", Instant.EPOCH.toString()));
        return new IslandLevelSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            longValue(json, "level", 0L),
            text(json, "worth", "0"),
            instant(calculatedAt)
        );
    }

    private static List<IslandRankSnapshot> rankings(String json) {
        List<IslandRankSnapshot> rankings = new ArrayList<>();
        for (String object : objects(json, "rankings")) {
            rankings.add(new IslandRankSnapshot(
                uuid(object, "islandId", new UUID(0L, 0L)),
                longValue(object, "level", 0L),
                text(object, "worth", "0"),
                instant(text(object, "calculatedAt", Instant.EPOCH.toString()))
            ));
        }
        return rankings;
    }

    private static List<IslandUpgradeSnapshot> upgrades(String json) {
        List<IslandUpgradeSnapshot> upgrades = new ArrayList<>();
        for (String object : objects(json, "upgrades")) {
            upgrades.add(upgrade(object));
        }
        return upgrades;
    }

    private static UpgradePurchaseSnapshot upgradePurchase(String json) {
        return new UpgradePurchaseSnapshot(
            bool(json, "accepted", false),
            text(json, "code", ""),
            text(json, "cost", "0"),
            json == null || json.contains("\"upgrade\":null") ? null : upgrade(json)
        );
    }

    private static IslandUpgradeSnapshot upgrade(String json) {
        return new IslandUpgradeSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "upgradeKey", ""),
            enumValue(UpgradeType.class, text(json, "type", "ISLAND_SIZE"), UpgradeType.ISLAND_SIZE),
            integer(json, "level", 0),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        );
    }

    private static List<UpgradeRuleSnapshot> upgradeRules(String json) {
        List<UpgradeRuleSnapshot> rules = new ArrayList<>();
        for (String object : objects(json, "rules")) {
            rules.add(new UpgradeRuleSnapshot(
                text(object, "upgradeKey", ""),
                enumValue(UpgradeType.class, text(object, "type", "ISLAND_SIZE"), UpgradeType.ISLAND_SIZE),
                integer(object, "maxLevel", 0),
                text(object, "baseCost", "0"),
                text(object, "multiplier", "1")
            ));
        }
        return rules;
    }

    private static List<BlockValueSnapshot> blockValues(String json) {
        List<BlockValueSnapshot> values = new ArrayList<>();
        for (String object : objects(json, "values")) {
            values.add(new BlockValueSnapshot(
                text(object, "materialKey", ""),
                text(object, "worth", "0"),
                longValue(object, "levelPoints", 0L),
                longValue(object, "limit", 0L)
            ));
        }
        return values;
    }

    private static List<IslandMissionSnapshot> missions(String json) {
        List<IslandMissionSnapshot> missions = new ArrayList<>();
        for (String object : objects(json, "missions")) {
            mission(object).ifPresent(missions::add);
        }
        return missions;
    }

    private static Optional<IslandMissionSnapshot> mission(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        return Optional.of(new IslandMissionSnapshot(
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "missionKey", ""),
            text(json, "kind", "MISSION"),
            text(json, "title", ""),
            longValue(json, "progress", 0L),
            longValue(json, "goal", 0L),
            bool(json, "completed", false),
            text(json, "reward", ""),
            instant(text(json, "updatedAt", Instant.EPOCH.toString()))
        ));
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

    private static NodeSweepResult nodeSweep(String json) {
        return new NodeSweepResult(stringArray(json, "nodes"), integer(json, "recoveryRequired", 0));
    }

    private static List<String> stringArray(String json, String field) {
        List<String> values = new ArrayList<>();
        String needle = "\"" + field + "\":[";
        int start = json.indexOf(needle);
        if (start < 0) {
            return values;
        }
        int index = start + needle.length();
        int end = json.indexOf(']', index);
        if (end < 0) {
            return values;
        }
        while (index < end) {
            int quote = json.indexOf('"', index);
            if (quote < 0 || quote >= end) {
                break;
            }
            int close = json.indexOf('"', quote + 1);
            if (close < 0 || close > end) {
                break;
            }
            values.add(json.substring(quote + 1, close));
            index = close + 1;
        }
        return values;
    }

    private static List<IslandNodeSnapshot> nodes(String json) {
        List<IslandNodeSnapshot> nodes = new ArrayList<>();
        for (String object : objects(json, "nodes")) {
            node(object).ifPresent(nodes::add);
        }
        return nodes;
    }

    private static Optional<IslandNodeSnapshot> node(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        return Optional.of(new IslandNodeSnapshot(
            text(json, "id", ""),
            text(json, "pool", "island"),
            text(json, "server", ""),
            text(json, "nodeVersion", ""),
            enumValue(NodeState.class, text(json, "state", "DOWN"), NodeState.DOWN),
            integer(json, "players", 0),
            integer(json, "softPlayerCap", 0),
            integer(json, "hardPlayerCap", 0),
            integer(json, "reservedSlots", 0),
            integer(json, "activeIslands", 0),
            integer(json, "maxActiveIslands", 0),
            decimal(json, "mspt", 0.0D),
            integer(json, "activationQueue", 0),
            integer(json, "maxActivationQueue", 0),
            decimal(json, "chunkLoadPressure", 0.0D),
            longValue(json, "heapUsedMb", 0L),
            longValue(json, "heapMaxMb", 0L),
            integer(json, "recentFailurePenalty", 0),
            bool(json, "storageAvailable", false),
            text(json, "supportedTemplates", ""),
            instant(text(json, "lastHeartbeat", Instant.EPOCH.toString())),
            decimal(json, "score", 0.0D),
            decimalMap(json, "scoreBreakdown"),
            bool(json, "eligibleForNewActivation", false),
            text(json, "allocationBlockReason", ""),
            levelScan(objectValue(json, "levelScan")),
            storage(objectValue(json, "storage"))
        ));
    }

    private static NodeLevelScanSnapshot levelScan(String json) {
        if (json == null || json.isBlank()) {
            return NodeLevelScanSnapshot.empty();
        }
        return new NodeLevelScanSnapshot(
            bool(json, "running", false),
            text(json, "lastIsland", ""),
            longValue(json, "startedAt", 0L),
            longValue(json, "finishedAt", 0L),
            longValue(json, "failedAt", 0L)
        );
    }

    private static NodeStorageSnapshot storage(String json) {
        if (json == null || json.isBlank()) {
            return NodeStorageSnapshot.empty();
        }
        return new NodeStorageSnapshot(
            decimal(json, "uploadSeconds", 0.0D),
            decimal(json, "downloadSeconds", 0.0D),
            longValue(json, "healthCheckFailures", 0L),
            longValue(json, "uploadFailures", 0L),
            longValue(json, "downloadFailures", 0L),
            longValue(json, "operationFailures", 0L)
        );
    }

    private static List<IslandTemplateSnapshot> templates(String json) {
        List<IslandTemplateSnapshot> templates = new ArrayList<>();
        for (String object : objects(json, "templates")) {
            templates.add(template(object));
        }
        return templates;
    }

    private static IslandTemplateSnapshot template(String json) {
        return new IslandTemplateSnapshot(
            text(json, "id", ""),
            text(json, "displayName", ""),
            bool(json, "enabled", false),
            text(json, "minNodeVersion", "")
        );
    }

    private static MigrationRunSnapshot migrationRun(String json) {
        return new MigrationRunSnapshot(
            text(json, "state", ""),
            text(json, "path", ""),
            integer(json, "manifests", 0),
            bool(json, "canImport", false),
            bool(json, "imported", false),
            integer(json, "importedIslands", 0),
            bool(json, "passed", false),
            integer(json, "expected", 0),
            bool(json, "rolledBack", false),
            integer(json, "removedIslands", 0),
            integer(json, "extractedBundles", 0),
            longValue(json, "extractedFiles", 0L),
            longValue(json, "extractedBytes", 0L),
            integer(json, "members", 0),
            integer(json, "bannedVisitors", 0),
            integer(json, "homes", 0),
            integer(json, "warps", 0),
            integer(json, "flags", 0),
            integer(json, "permissions", 0),
            integer(json, "upgrades", 0),
            integer(json, "limits", 0),
            integer(json, "completedMissions", 0),
            integer(json, "blockValues", 0),
            integer(json, "blockCounts", 0),
            integer(json, "blockingIssues", 0),
            integer(json, "warningIssues", 0),
            migrationIssues(json)
        );
    }

    private static List<MigrationIssueSnapshot> migrationIssues(String json) {
        List<MigrationIssueSnapshot> issues = new ArrayList<>();
        for (String object : objects(json, "issues")) {
            issues.add(new MigrationIssueSnapshot(
                text(object, "code", ""),
                text(object, "message", ""),
                bool(object, "blocking", false)
            ));
        }
        return issues;
    }

    private static List<GlobalEventSnapshot> events(String json) {
        List<GlobalEventSnapshot> events = new ArrayList<>();
        for (String object : objects(json, "events")) {
            events.add(new GlobalEventSnapshot(
                number(object, "seq"),
                text(object, "type", ""),
                stringMap(object, "fields"),
                instant(text(object, "occurredAt", Instant.EPOCH.toString()))
            ));
        }
        return events;
    }

    private static GlobalEventBatchSnapshot eventBatch(String json) {
        return new GlobalEventBatchSnapshot(
            number(json, "oldestSeq"),
            number(json, "latestSeq"),
            events(json)
        );
    }

    private static List<AuditLogSnapshot> auditLogs(String json) {
        List<AuditLogSnapshot> audit = new ArrayList<>();
        for (String object : objects(json, "audit")) {
            audit.add(new AuditLogSnapshot(
                uuid(object, "id", new UUID(0L, 0L)),
                nullableUuid(object, "actorUuid"),
                text(object, "actorType", ""),
                text(object, "action", ""),
                text(object, "targetType", ""),
                text(object, "targetId", ""),
                stringMap(object, "payload"),
                instant(text(object, "createdAt", Instant.EPOCH.toString()))
            ));
        }
        return audit;
    }

    private static Optional<RouteTicket> routeTicket(String json) {
        if (json == null || json.isBlank() || json.contains("\"error\"")) {
            return Optional.empty();
        }
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        payload.put("targetServerName", text(json, "targetServerName", text(json, "targetNode", "")));
        putPayloadIfPresent(payload, json, "homeName");
        putPayloadIfPresent(payload, json, "warpName");
        putPayloadIfPresent(payload, json, "localX");
        putPayloadIfPresent(payload, json, "localY");
        putPayloadIfPresent(payload, json, "localZ");
        putPayloadIfPresent(payload, json, "yaw");
        putPayloadIfPresent(payload, json, "pitch");
        return Optional.of(new RouteTicket(
            uuid(json, "ticketId", new UUID(0L, 0L)),
            uuid(json, "playerUuid", new UUID(0L, 0L)),
            enumValue(RouteAction.class, text(json, "action", "HOME"), RouteAction.HOME),
            uuid(json, "islandId", new UUID(0L, 0L)),
            text(json, "targetNode", ""),
            text(json, "targetWorld", ""),
            enumValue(RouteTicketState.class, text(json, "state", "READY"), RouteTicketState.READY),
            instant(text(json, "expiresAt", Instant.EPOCH.toString())),
            text(json, "nonce", ""),
            Map.copyOf(payload)
        ));
    }

    private static RouteClearResult routeClear(String json) {
        return new RouteClearResult(
            bool(json, "clearedSession", false),
            bool(json, "clearedTicket", false)
        );
    }

    private static CoreMaintenanceResult maintenance(String json, boolean reloaded) {
        return new CoreMaintenanceResult(
            reloaded,
            integer(json, "clearedSessions", 0),
            integer(json, "clearedTickets", 0)
        );
    }

    private static JobRecoveryResult jobRecovery(String json) {
        boolean accepted = json != null && !json.isBlank() && !json.contains("\"error\"");
        return new JobRecoveryResult(accepted, accepted ? text(json, "recovered", scalar(json, "recovered")) : "", accepted ? "RECOVERED" : text(json, "code", "FAILED"));
    }

    private static void putPayloadIfPresent(Map<String, String> payload, String json, String field) {
        String value = text(json, field, null);
        if (value != null) {
            payload.put(field, value);
            return;
        }
        String scalar = scalar(json, field);
        if (scalar != null) {
            payload.put(field, scalar);
        }
    }

    private static List<IslandJobSnapshot> jobs(String json) {
        List<IslandJobSnapshot> jobs = new ArrayList<>();
        for (String object : objects(json, "jobs")) {
            jobs.add(new IslandJobSnapshot(
                uuid(object, "id", uuid(object, "jobId", new UUID(0L, 0L))),
                text(object, "type", ""),
                uuid(object, "islandId", new UUID(0L, 0L)),
                text(object, "targetNode", ""),
                text(object, "state", ""),
                integer(object, "priority", 0),
                integer(object, "attempts", 0),
                text(object, "lockedBy", ""),
                text(object, "errorMessage", ""),
                stringMap(object, "payload"),
                instant(text(object, "createdAt", Instant.EPOCH.toString())),
                instant(text(object, "updatedAt", Instant.EPOCH.toString()))
            ));
        }
        return jobs;
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
        int cursor = 0;
        while (cursor < object.length()) {
            while (cursor < object.length() && (object.charAt(cursor) == ',' || Character.isWhitespace(object.charAt(cursor)))) {
                cursor++;
            }
            if (cursor >= object.length() || object.charAt(cursor) != '"') {
                break;
            }
            int keyEnd = nextQuote(object, cursor + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = unescape(object.substring(cursor + 1, keyEnd));
            int separator = object.indexOf(':', keyEnd + 1);
            if (separator < 0) {
                break;
            }
            int valueStart = separator + 1;
            while (valueStart < object.length() && Character.isWhitespace(object.charAt(valueStart))) {
                valueStart++;
            }
            if (valueStart >= object.length() || object.charAt(valueStart) != '"') {
                break;
            }
            int valueEnd = nextQuote(object, valueStart + 1);
            if (valueEnd < 0) {
                break;
            }
            values.put(key, unescape(object.substring(valueStart + 1, valueEnd)));
            cursor = valueEnd + 1;
        }
        return Map.copyOf(values);
    }

    private static String objectValue(String json, String field) {
        String needle = "\"" + field + "\":{";
        int start = json == null ? -1 : json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int objectStart = start + needle.length() - 1;
        int index = objectStart;
        int depth = 0;
        while (index < json.length()) {
            char ch = json.charAt(index);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart, index + 1);
                }
            }
            index++;
        }
        return "";
    }

    private static Map<String, Double> decimalMap(String json, String field) {
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
        java.util.LinkedHashMap<String, Double> values = new java.util.LinkedHashMap<>();
        int cursor = 0;
        while (cursor < object.length()) {
            while (cursor < object.length() && (object.charAt(cursor) == ',' || Character.isWhitespace(object.charAt(cursor)))) {
                cursor++;
            }
            if (cursor >= object.length() || object.charAt(cursor) != '"') {
                break;
            }
            int keyEnd = nextQuote(object, cursor + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = unescape(object.substring(cursor + 1, keyEnd));
            int separator = object.indexOf(':', keyEnd + 1);
            if (separator < 0) {
                break;
            }
            int valueStart = separator + 1;
            int valueEnd = valueStart;
            while (valueEnd < object.length() && object.charAt(valueEnd) != ',') {
                valueEnd++;
            }
            try {
                values.put(key, Double.parseDouble(object.substring(valueStart, valueEnd).replace("\"", "").trim()));
            } catch (NumberFormatException ignored) {
                values.put(key, 0.0D);
            }
            cursor = valueEnd + 1;
        }
        return Map.copyOf(values);
    }

    private static int nextQuote(String value, int start) {
        int index = start;
        boolean escaped = false;
        while (index < value.length()) {
            char ch = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == '"') {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
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

    private static UUID nullableUuid(String json, String field) {
        String value = nullableText(json, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (RuntimeException ignored) {
            return null;
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

    private static String scalar(String json, String field) {
        String needle = "\"" + field + "\":";
        int start = json.indexOf(needle);
        if (start < 0) {
            return null;
        }
        int valueStart = start + needle.length();
        int end = valueStart;
        while (end < json.length() && "0123456789.-".indexOf(json.charAt(end)) >= 0) {
            end++;
        }
        return end == valueStart ? null : json.substring(valueStart, end);
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
