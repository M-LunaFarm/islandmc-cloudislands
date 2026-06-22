package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.AddonStateBulkLoadRequest;
import kr.lunaf.cloudislands.api.model.AddonStateBulkSaveRequest;
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
        return new CoreIslandQueryClient(this);
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
        return new CoreHomeWarpQueryClient(this);
    }

    default HomeWarpCommandClient homeWarpCommands() {
        return new CoreHomeWarpCommandClient(this);
    }

    default NavigationQueryClient navigation() {
        return new CoreNavigationQueryClient(this);
    }

    default NavigationCommandClient navigationCommands() {
        return new CoreNavigationCommandClient(this);
    }

    default IslandVisitorStatsQueryClient visitorStats() {
        return new CoreIslandVisitorStatsQueryClient(this);
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
        return new CoreMemberQueryClient(this);
    }

    default MemberCommandClient memberCommands() {
        return new CoreMemberCommandClient(this);
    }

    default AdminNodeQueryClient adminNodes() {
        return new CoreAdminNodeQueryClient(this);
    }

    default AdminNodeCommandClient adminNodeCommands() {
        return new CoreAdminNodeCommandClient(this);
    }

    default AdminIslandQueryClient adminIslands() {
        return new CoreAdminIslandQueryClient(this);
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
        return new CoreBlockValueQueryClient(this);
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
        return new CoreAdminAddonStateQueryClient(this);
    }

    default AddonStateClient addonStates() {
        return new CoreAddonStateClient(this);
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
    CompletableFuture<String> islandInfo(UUID islandId);
    CompletableFuture<String> islandInfoByOwner(UUID ownerUuid);
    CompletableFuture<String> islandInfoByName(String name);
    CompletableFuture<String> getIsland(UUID islandId);
    CompletableFuture<String> getIslandByOwner(UUID ownerUuid);
    CompletableFuture<String> getIslandMembers(UUID islandId);
    CompletableFuture<String> getIslandRuntime(UUID islandId);
    CompletableFuture<String> getIslandFlags(UUID islandId);
    CompletableFuture<String> getIslandLevel(UUID islandId);
    CompletableFuture<Void> setIslandName(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<String> setIslandNameResult(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<String> listIslandMembers(UUID islandId);
    CompletableFuture<Void> setIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role);
    CompletableFuture<String> setIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid, IslandRole role);
    CompletableFuture<String> setIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid, String roleKey);
    CompletableFuture<String> trustIslandMemberTemporary(UUID islandId, UUID actorUuid, UUID playerUuid, long durationSeconds);
    CompletableFuture<Void> transferIslandOwnership(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<String> transferIslandOwnershipResult(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<Void> removeIslandMember(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<String> removeIslandMemberResult(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<String> createIslandInvite(UUID islandId, UUID inviterUuid, UUID targetUuid);
    CompletableFuture<String> listPendingInvites(UUID playerUuid);
    CompletableFuture<String> listPlayerIslands(UUID playerUuid);
    CompletableFuture<Void> acceptIslandInvite(UUID inviteId, UUID playerUuid);
    CompletableFuture<String> acceptIslandInviteResult(UUID inviteId, UUID playerUuid);
    CompletableFuture<Void> declineIslandInvite(UUID inviteId, UUID playerUuid);
    CompletableFuture<String> declineIslandInviteResult(UUID inviteId, UUID playerUuid);
    CompletableFuture<Void> banIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason);
    CompletableFuture<String> banIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid, String reason);
    CompletableFuture<String> listIslandBans(UUID islandId);
    CompletableFuture<Void> pardonIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<String> pardonIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<Void> kickIslandVisitor(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<String> kickIslandVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid);
    CompletableFuture<String> islandVisitorStats(UUID islandId, int limit);
    CompletableFuture<String> listIslandFlags(UUID islandId);
    CompletableFuture<Void> setIslandFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<String> setIslandFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<String> islandBiome(UUID islandId);
    CompletableFuture<Void> setIslandBiome(UUID islandId, UUID actorUuid, String biomeKey);
    CompletableFuture<String> setIslandBiomeResult(UUID islandId, UUID actorUuid, String biomeKey);
    CompletableFuture<String> listIslandHomes(UUID islandId);
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
    CompletableFuture<String> listIslandWarps(UUID islandId);
    CompletableFuture<String> listPublicWarps(int limit);
    CompletableFuture<String> listPublicWarps(int limit, String category, String query);
    CompletableFuture<String> listIslandReviews(UUID islandId, int limit);
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
    CompletableFuture<String> listPublicIslands(int limit);
    CompletableFuture<String> listBlockValues();
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
    CompletableFuture<String> listNodes();
    CompletableFuture<String> nodeInfo(String nodeId);
    CompletableFuture<String> nodeIslands(String nodeId, int limit);
    CompletableFuture<String> drainNode(String nodeId);
    CompletableFuture<String> drainNodeResult(String nodeId);
    default CompletableFuture<String> drainNodePath(String nodeId) {
        return drainNodeResult(nodeId);
    }
    CompletableFuture<String> undrainNode(String nodeId);
    CompletableFuture<String> undrainNodeResult(String nodeId);
    default CompletableFuture<String> undrainNodePath(String nodeId) {
        return undrainNodeResult(nodeId);
    }
    CompletableFuture<String> sweepNode(String nodeId);
    CompletableFuture<String> sweepNodeResult(String nodeId);
    default CompletableFuture<String> sweepNodePath(String nodeId) {
        return sweepNodeResult(nodeId);
    }
    CompletableFuture<String> kickAllNode(String nodeId, String reason);
    CompletableFuture<String> kickAllNodeResult(String nodeId, String reason);
    CompletableFuture<String> shutdownNodeSafely(String nodeId, String reason);
    CompletableFuture<String> shutdownNodeSafelyResult(String nodeId, String reason);
    default CompletableFuture<String> shutdownNodeSafelyPath(String nodeId, String reason) {
        return shutdownNodeSafelyResult(nodeId, reason);
    }
    CompletableFuture<String> adminIslandInfo(UUID lookupUuid);
    CompletableFuture<String> adminIslandWhere(UUID islandId);
    CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId);
    CompletableFuture<String> addonStateSummary();
    CompletableFuture<String> addonState(String addonId);
    CompletableFuture<String> putAddonState(String addonId, String key, String value);
    CompletableFuture<String> putAddonState(String addonId, Map<String, String> values);
    CompletableFuture<String> putAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables);
    default CompletableFuture<String> saveAddonState(String addonId, Map<String, String> values) {
        return putAddonState(addonId, values);
    }
    default CompletableFuture<String> saveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return putAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> bulkSaveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return saveAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return bulkSaveAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAddonState(AddonStateBulkSaveRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Addon state request is required"));
        }
        if (request.islandScoped()) {
            return tableKeyValueBulkSaveAddonIslandState(request);
        }
        return putAddonState(request.addonId(), request.flattenedStateValues());
    }
    default CompletableFuture<String> bulkSaveAddonTableKeyValueState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> saveAddonTableKeyValueState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAliasAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkAddonState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> bulkAddonTableKeyValueState(String addonId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkAddonState(addonId, values, tables);
    }
    default CompletableFuture<String> tableBulkAddonState(String addonId, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonState(addonId, Map.of(), tables);
    }
    default CompletableFuture<String> bulkAddonTableState(String addonId, Map<String, Map<String, String>> tables) {
        return tableBulkAddonState(addonId, tables);
    }
    default CompletableFuture<String> tableBulkSetAddonState(String addonId, Map<String, Map<String, String>> tables) {
        return tableBulkAddonState(addonId, tables);
    }
    default CompletableFuture<String> bulkSetAddonTableState(String addonId, Map<String, Map<String, String>> tables) {
        return tableBulkSetAddonState(addonId, tables);
    }
    CompletableFuture<String> addonTableState(String addonId, String table);
    default CompletableFuture<String> tableKeyValueBulkLoadAddonState(String addonId, String table) {
        return addonTableState(addonId, table);
    }
    default CompletableFuture<String> tableKeyValueBulkLoadAddonState(AddonStateBulkLoadRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Addon state request is required"));
        }
        if (request.islandScoped()) {
            return tableKeyValueBulkLoadAddonIslandState(request);
        }
        return tableKeyValueBulkLoadAddonState(request.addonId(), request.table());
    }
    default CompletableFuture<String> bulkLoadAddonTableKeyValueState(String addonId, String table) {
        return tableKeyValueBulkLoadAddonState(addonId, table);
    }
    default CompletableFuture<String> bulkLoadAddonTableKeyValueState(AddonStateBulkLoadRequest request) {
        return tableKeyValueBulkLoadAddonState(request);
    }
    default CompletableFuture<String> tableLoadAddonState(String addonId, String table) {
        return tableKeyValueBulkLoadAddonState(addonId, table);
    }
    default CompletableFuture<String> tableLoadAddonState(AddonStateBulkLoadRequest request) {
        return tableKeyValueBulkLoadAddonState(request);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAddonState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, Map.of(), table == null ? Map.of() : Map.of(table, values == null ? Map.of() : values));
    }
    default CompletableFuture<String> bulkSaveAddonTableKeyValueState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, table, values);
    }
    default CompletableFuture<String> saveAddonTableKeyValueState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, table, values);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAliasAddonState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, table, values);
    }
    default CompletableFuture<String> tableKeyValueBulkAddonState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, table, values);
    }
    default CompletableFuture<String> bulkAddonTableKeyValueState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkAddonState(addonId, table, values);
    }
    default CompletableFuture<String> tableBulkSetAddonState(String addonId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonState(addonId, table, values);
    }
    default CompletableFuture<String> bulkSetAddonTableState(String addonId, String table, Map<String, String> values) {
        return tableBulkSetAddonState(addonId, table, values);
    }
    CompletableFuture<String> putAddonTableState(String addonId, String table, Map<String, String> values);
    default CompletableFuture<String> saveAddonTableState(String addonId, String table, Map<String, String> values) {
        return putAddonTableState(addonId, table, values);
    }
    CompletableFuture<String> replaceAddonTableState(String addonId, String table, Map<String, String> values);
    CompletableFuture<String> clearAddonTableState(String addonId, String table);
    CompletableFuture<String> removeAddonState(String addonId, String key);
    CompletableFuture<String> clearAddonState(String addonId);
    CompletableFuture<String> addonIslandState(String addonId, UUID islandId);
    CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, String key, String value);
    CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, Map<String, String> values);
    CompletableFuture<String> putAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables);
    default CompletableFuture<String> saveAddonIslandState(String addonId, UUID islandId, Map<String, String> values) {
        return putAddonIslandState(addonId, islandId, values);
    }
    default CompletableFuture<String> saveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return putAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> bulkSaveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return saveAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return bulkSaveAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAddonIslandState(AddonStateBulkSaveRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Addon state request is required"));
        }
        if (!request.islandScoped()) {
            return tableKeyValueBulkSaveAddonState(request);
        }
        return putAddonIslandState(request.addonId(), request.islandId(), request.flattenedStateValues());
    }
    default CompletableFuture<String> bulkSaveAddonIslandTableKeyValueState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> saveAddonIslandTableKeyValueState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAliasAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> tableKeyValueBulkAddonIslandState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> bulkAddonIslandTableKeyValueState(String addonId, UUID islandId, Map<String, String> values, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkAddonIslandState(addonId, islandId, values, tables);
    }
    default CompletableFuture<String> tableBulkAddonIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, Map.of(), tables);
    }
    default CompletableFuture<String> bulkAddonIslandTableState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableBulkAddonIslandState(addonId, islandId, tables);
    }
    default CompletableFuture<String> tableBulkSetAddonIslandState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableBulkAddonIslandState(addonId, islandId, tables);
    }
    default CompletableFuture<String> bulkSetAddonIslandTableState(String addonId, UUID islandId, Map<String, Map<String, String>> tables) {
        return tableBulkSetAddonIslandState(addonId, islandId, tables);
    }
    CompletableFuture<String> addonIslandTableState(String addonId, UUID islandId, String table);
    default CompletableFuture<String> tableKeyValueBulkLoadAddonIslandState(String addonId, UUID islandId, String table) {
        return addonIslandTableState(addonId, islandId, table);
    }
    default CompletableFuture<String> tableKeyValueBulkLoadAddonIslandState(AddonStateBulkLoadRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Addon state request is required"));
        }
        if (!request.islandScoped()) {
            return tableKeyValueBulkLoadAddonState(request);
        }
        return tableKeyValueBulkLoadAddonIslandState(request.addonId(), request.islandId(), request.table());
    }
    default CompletableFuture<String> bulkLoadAddonIslandTableKeyValueState(String addonId, UUID islandId, String table) {
        return tableKeyValueBulkLoadAddonIslandState(addonId, islandId, table);
    }
    default CompletableFuture<String> bulkLoadAddonIslandTableKeyValueState(AddonStateBulkLoadRequest request) {
        return tableKeyValueBulkLoadAddonIslandState(request);
    }
    default CompletableFuture<String> tableLoadAddonIslandState(String addonId, UUID islandId, String table) {
        return tableKeyValueBulkLoadAddonIslandState(addonId, islandId, table);
    }
    default CompletableFuture<String> tableLoadAddonIslandState(AddonStateBulkLoadRequest request) {
        return tableKeyValueBulkLoadAddonIslandState(request);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAddonIslandState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, Map.of(), table == null ? Map.of() : Map.of(table, values == null ? Map.of() : values));
    }
    default CompletableFuture<String> bulkSaveAddonIslandTableKeyValueState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, table, values);
    }
    default CompletableFuture<String> saveAddonIslandTableKeyValueState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, table, values);
    }
    default CompletableFuture<String> tableKeyValueBulkSaveAliasAddonIslandState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, table, values);
    }
    default CompletableFuture<String> tableKeyValueBulkAddonIslandState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, table, values);
    }
    default CompletableFuture<String> bulkAddonIslandTableKeyValueState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkAddonIslandState(addonId, islandId, table, values);
    }
    default CompletableFuture<String> tableBulkSetAddonIslandState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableKeyValueBulkSaveAddonIslandState(addonId, islandId, table, values);
    }
    default CompletableFuture<String> bulkSetAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return tableBulkSetAddonIslandState(addonId, islandId, table, values);
    }
    CompletableFuture<String> putAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values);
    default CompletableFuture<String> saveAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values) {
        return putAddonIslandTableState(addonId, islandId, table, values);
    }
    CompletableFuture<String> replaceAddonIslandTableState(String addonId, UUID islandId, String table, Map<String, String> values);
    CompletableFuture<String> clearAddonIslandTableState(String addonId, UUID islandId, String table);
    CompletableFuture<String> removeAddonIslandState(String addonId, UUID islandId, String key);
    CompletableFuture<String> clearAddonIslandState(String addonId, UUID islandId);
    CompletableFuture<String> migrateSuperiorSkyblock2(String action, String path);
    CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    CompletableFuture<String> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<String> failJobResult(String nodeId, UUID jobId, String errorMessage);
    CompletableFuture<String> publishHeartbeatResult(NodeHeartbeatRequest request);
}
