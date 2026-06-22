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
        if (this instanceof IslandEnvironmentQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed environment queries");
    }

    default IslandEnvironmentCommandClient environmentCommands() {
        if (this instanceof IslandEnvironmentCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed environment commands");
    }

    default IslandSettingsCommandClient settingsCommands() {
        if (this instanceof IslandSettingsCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed settings commands");
    }

    default PermissionCommandClient permissions() {
        if (this instanceof PermissionCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed permission commands");
    }

    default PermissionQueryClient permissionQueries() {
        if (this instanceof PermissionQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed permission queries");
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
        if (this instanceof HomeWarpCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed home and warp commands");
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
        if (this instanceof ProgressionQueryClient queries) {
            return queries;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed progression queries");
    }

    default ProgressionCommandClient progressionCommands() {
        if (this instanceof ProgressionCommandClient commands) {
            return commands;
        }
        throw new UnsupportedOperationException("CoreApiClient implementation does not provide typed progression commands");
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
    CompletableFuture<String> setIslandReview(UUID islandId, UUID reviewerUuid, int rating, String comment);
    CompletableFuture<String> deleteIslandReview(UUID islandId, UUID reviewerUuid);
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
}
