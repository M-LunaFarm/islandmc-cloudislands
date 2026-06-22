package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RouteTicket;
import kr.lunaf.cloudislands.protocol.job.IslandJob;
import kr.lunaf.cloudislands.protocol.job.IslandJobType;
import kr.lunaf.cloudislands.protocol.node.NodeHeartbeatRequest;
import kr.lunaf.cloudislands.protocol.session.PlayerRouteSession;

public interface CoreApiClient {
    default IslandQueryClient islands() {
        if (this instanceof IslandQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed island queries");
    }

    default IslandEnvironmentQueryClient environment() {
        return new CoreIslandEnvironmentQueryClient(this);
    }

    default IslandEnvironmentCommandClient environmentCommands() {
        return new CoreIslandEnvironmentCommandClient(this);
    }

    default IslandSettingsCommandClient settingsCommands() {
        return new CoreIslandSettingsCommandClient(this);
    }

    default PermissionCommandClient permissions() {
        return new CorePermissionCommandClient(this);
    }

    default PermissionQueryClient permissionQueries() {
        return new CorePermissionQueryClient(this);
    }

    default SnapshotQueryClient snapshots() {
        if (this instanceof SnapshotQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed snapshot queries");
    }

    default SnapshotCommandClient snapshotCommands() {
        if (this instanceof SnapshotCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed snapshot commands");
    }

    default CommunicationQueryClient communication() {
        if (this instanceof CommunicationQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed communication queries");
    }

    default CommunicationCommandClient communicationCommands() {
        if (this instanceof CommunicationCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed communication commands");
    }

    default BankQueryClient bank() {
        if (this instanceof BankQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed bank queries");
    }

    default BankCommandClient bankCommands() {
        if (this instanceof BankCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed bank commands");
    }

    default WarehouseQueryClient warehouse() {
        if (this instanceof WarehouseQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed warehouse queries");
    }

    default WarehouseCommandClient warehouseCommands() {
        if (this instanceof WarehouseCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed warehouse commands");
    }

    default HomeWarpQueryClient homeWarps() {
        if (this instanceof HomeWarpQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed home and warp queries");
    }

    default HomeWarpCommandClient homeWarpCommands() {
        return new CoreHomeWarpCommandClient(this);
    }

    default NavigationQueryClient navigation() {
        if (this instanceof NavigationQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed navigation queries");
    }

    default NavigationCommandClient navigationCommands() {
        return new CoreNavigationCommandClient(this);
    }

    default IslandVisitorStatsQueryClient visitorStats() {
        if (this instanceof IslandVisitorStatsQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed visitor stats queries");
    }

    default RoutingCommandClient routingCommands() {
        if (this instanceof RoutingCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed routing commands");
    }

    default RuntimeCommandClient runtimeCommands() {
        if (this instanceof RuntimeCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed runtime commands");
    }

    default ProgressionQueryClient progression() {
        return new CoreProgressionQueryClient(this);
    }

    default ProgressionCommandClient progressionCommands() {
        return new CoreProgressionCommandClient(this);
    }

    default MemberQueryClient members() {
        if (this instanceof MemberQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed member queries");
    }

    default MemberCommandClient memberCommands() {
        if (this instanceof MemberCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed member commands");
    }

    default AdminNodeQueryClient adminNodes() {
        if (this instanceof AdminNodeQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin node queries");
    }

    default AdminNodeCommandClient adminNodeCommands() {
        if (this instanceof AdminNodeCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin node commands");
    }

    default AdminIslandQueryClient adminIslands() {
        if (this instanceof AdminIslandQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin island queries");
    }

    default IslandLifecycleCommandClient lifecycle() {
        if (this instanceof IslandLifecycleCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed lifecycle commands");
    }

    default PlayerProfileQueryClient playerProfiles() {
        if (this instanceof PlayerProfileQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed player profile queries");
    }

    default PlayerProfileCommandClient playerProfileCommands() {
        if (this instanceof PlayerProfileCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed player profile commands");
    }

    default JobQueryClient jobs() {
        if (this instanceof JobQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed job queries");
    }

    default JobCommandClient jobCommands() {
        if (this instanceof JobCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed job commands");
    }

    default TemplateQueryClient templates() {
        if (this instanceof TemplateQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed template queries");
    }

    default TemplateCommandClient templateCommands() {
        if (this instanceof TemplateCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed template commands");
    }

    default BlockValueQueryClient blockValues() {
        if (this instanceof BlockValueQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed block value queries");
    }

    default BlockValueCommandClient blockValueCommands() {
        if (this instanceof BlockValueCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed block value commands");
    }

    default AdminRouteClient adminRoutes() {
        if (this instanceof AdminRouteClient client) {
            return client;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin route queries");
    }

    default AdminEventQueryClient adminEvents() {
        if (this instanceof AdminEventQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin event queries");
    }

    default AdminAuditQueryClient adminAudit() {
        if (this instanceof AdminAuditQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin audit queries");
    }

    default AdminStorageQueryClient adminStorage() {
        if (this instanceof AdminStorageQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin storage queries");
    }

    default AdminMaintenanceCommandClient adminMaintenance() {
        if (this instanceof AdminMaintenanceCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin maintenance commands");
    }

    default AdminAddonStateQueryClient adminAddonState() {
        if (this instanceof AdminAddonStateQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin addon state queries");
    }

    default AddonStateClient addonStates() {
        if (this instanceof AddonStateClient states) {
            return states;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed addon state operations");
    }

    default AdminCoreConfigQueryClient adminCoreConfig() {
        if (this instanceof AdminCoreConfigQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin core config queries");
    }

    default AdminMetricsQueryClient adminMetrics() {
        if (this instanceof AdminMetricsQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed admin metrics queries");
    }

    CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId);
    CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId);
    CompletableFuture<String> getIslandRuntime(UUID islandId);
    CompletableFuture<String> getIslandFlags(UUID islandId);
    CompletableFuture<String> getIslandLevel(UUID islandId);
    CompletableFuture<Void> setIslandName(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<String> setIslandNameResult(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<String> listIslandFlags(UUID islandId);
    CompletableFuture<Void> setIslandFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<String> setIslandFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<String> islandBiome(UUID islandId);
    CompletableFuture<Void> setIslandBiome(UUID islandId, UUID actorUuid, String biomeKey);
    CompletableFuture<String> setIslandBiomeResult(UUID islandId, UUID actorUuid, String biomeKey);
    CompletableFuture<Void> setIslandHome(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<String> setIslandHomeResult(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<String> listIslandPermissions(UUID islandId);
    CompletableFuture<Void> setIslandPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed);
    CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed);
    CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed);
    CompletableFuture<String> setIslandPermissionResult(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed, String expectedVersion);
    CompletableFuture<String> setIslandPermissionOverride(UUID islandId, UUID actorUuid, UUID playerUuid, IslandPermission permission, boolean allowed);
    CompletableFuture<String> listIslandRoles(UUID islandId);
    CompletableFuture<String> upsertIslandRole(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName);
    CompletableFuture<String> upsertIslandRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName);
    CompletableFuture<String> resetIslandRole(UUID islandId, UUID actorUuid, IslandRole role);
    CompletableFuture<String> resetIslandRole(UUID islandId, UUID actorUuid, String roleKey);
    CompletableFuture<String> setIslandReview(UUID islandId, UUID reviewerUuid, int rating, String comment);
    CompletableFuture<String> deleteIslandReview(UUID islandId, UUID reviewerUuid);
    CompletableFuture<Void> setIslandWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess);
    CompletableFuture<String> setIslandWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess);
    CompletableFuture<String> setIslandWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, String category);
    CompletableFuture<Void> deleteIslandWarp(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<String> deleteIslandWarpResult(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<Void> setIslandWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
    CompletableFuture<String> setIslandWarpPublicAccessResult(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
    CompletableFuture<Void> setIslandPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess);
    CompletableFuture<String> setIslandPublicAccessResult(UUID islandId, UUID actorUuid, boolean publicAccess);
    CompletableFuture<Void> setIslandLocked(UUID islandId, UUID actorUuid, boolean locked);
    CompletableFuture<String> setIslandLockedResult(UUID islandId, UUID actorUuid, boolean locked);
    CompletableFuture<String> recordBlockDeltaResult(UUID islandId, String materialKey, long delta);
    CompletableFuture<String> replaceBlockCounts(UUID islandId, Map<String, Long> counts);
    CompletableFuture<String> islandBlockDetails(UUID islandId, int limit);
    CompletableFuture<String> recalculateIslandLevel(UUID islandId, UUID actorUuid);
    CompletableFuture<String> topIslandsByLevel(int limit);
    CompletableFuture<String> topIslandsByWorth(int limit);
    CompletableFuture<String> topIslandsByReviews(int limit);
    CompletableFuture<String> listUpgradeRules();
    CompletableFuture<String> listIslandUpgrades(UUID islandId);
    CompletableFuture<String> purchaseIslandUpgrade(UUID islandId, UUID actorUuid, String upgradeKey);
    CompletableFuture<String> listIslandMissions(UUID islandId, String kind);
    default CompletableFuture<String> completeIslandMission(UUID islandId, UUID actorUuid, String missionKey) {
        return completeIslandMission(islandId, actorUuid, missionKey, "MISSION");
    }
    CompletableFuture<String> completeIslandMission(UUID islandId, UUID actorUuid, String missionKey, String kind);
    CompletableFuture<String> progressIslandMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount);
    CompletableFuture<String> registerMissionProvider(String providerId, String definitionsJson);
    CompletableFuture<String> listIslandLimits(UUID islandId);
    CompletableFuture<String> setIslandLimit(UUID islandId, UUID actorUuid, String limitKey, long value);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName);
    CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid);
    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid);
    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName);
    CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch);
    CompletableFuture<Optional<PlayerRouteSession>> findRouteSession(UUID playerUuid, String nodeId);
    CompletableFuture<Optional<PlayerRouteSession>> findAnyRouteSession(UUID playerUuid);
    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId);
    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, boolean reportMissing);
    CompletableFuture<Optional<PlayerRouteSession>> consumeRouteSession(UUID playerUuid, String nodeId, UUID ticketId, String nonce, boolean reportMissing);
    CompletableFuture<Optional<RouteTicket>> routeTicketStatus(UUID ticketId, UUID playerUuid, String nonce);
    CompletableFuture<Optional<RouteTicket>> consumeTicket(UUID ticketId, UUID playerUuid, String nodeId, String nonce);
    CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId);
    CompletableFuture<String> migrateSuperiorSkyblock2(String action, String path);
    CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    CompletableFuture<String> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<String> failJobResult(String nodeId, UUID jobId, String errorMessage);
    CompletableFuture<String> publishHeartbeatResult(NodeHeartbeatRequest request);
}
