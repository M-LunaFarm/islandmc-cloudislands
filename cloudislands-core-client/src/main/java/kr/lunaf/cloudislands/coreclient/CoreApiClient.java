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

    default PermissionCommandClient permissions() {
        return new CorePermissionCommandClient(this);
    }

    CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId);
    CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId);
    CompletableFuture<String> resetIsland(UUID islandId, UUID actorUuid, String reason);
    CompletableFuture<String> resetIslandResult(UUID islandId, UUID actorUuid, String reason);
    CompletableFuture<String> islandInfo(UUID islandId);
    CompletableFuture<String> islandInfoByOwner(UUID ownerUuid);
    CompletableFuture<String> islandInfoByName(String name);
    CompletableFuture<String> getIsland(UUID islandId);
    CompletableFuture<String> getIslandByOwner(UUID ownerUuid);
    CompletableFuture<String> getIslandMembers(UUID islandId);
    CompletableFuture<String> getIslandRuntime(UUID islandId);
    CompletableFuture<String> getIslandFlags(UUID islandId);
    CompletableFuture<String> getIslandLevel(UUID islandId);
    CompletableFuture<String> getPlayerProfile(UUID playerUuid);
    CompletableFuture<String> getPlayerIsland(UUID playerUuid);
    CompletableFuture<String> getJoinedIslands(UUID playerUuid);
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
    CompletableFuture<String> islandWarehouse(UUID islandId, int limit);
    CompletableFuture<String> depositIslandWarehouse(UUID islandId, UUID actorUuid, String materialKey, long amount);
    CompletableFuture<String> withdrawIslandWarehouse(UUID islandId, UUID actorUuid, String materialKey, long amount);
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
    CompletableFuture<Void> recordBlockDelta(UUID islandId, String materialKey, long delta);
    CompletableFuture<String> recordBlockDeltaResult(UUID islandId, String materialKey, long delta);
    CompletableFuture<String> replaceBlockCounts(UUID islandId, Map<String, Long> counts);
    CompletableFuture<String> islandBlockDetails(UUID islandId, int limit);
    CompletableFuture<String> recalculateIslandLevel(UUID islandId, UUID actorUuid);
    CompletableFuture<String> topIslandsByLevel(int limit);
    CompletableFuture<String> topIslandsByWorth(int limit);
    CompletableFuture<String> topIslandsByReviews(int limit);
    CompletableFuture<String> listPublicIslands(int limit);
    CompletableFuture<Void> setBlockValue(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit);
    CompletableFuture<String> setBlockValueResult(UUID actorUuid, String materialKey, String worth, long levelPoints, long limit);
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
    CompletableFuture<String> sendIslandChat(UUID islandId, UUID actorUuid, String channel, String message);
    CompletableFuture<String> listIslandSnapshots(UUID islandId, int limit);
    CompletableFuture<String> recordIslandSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId);
    default CompletableFuture<String> recordIslandSnapshot(UUID islandId, long snapshotNo, String storagePath, String reason, String checksum, long sizeBytes, String nodeId, long fencingToken) {
        return recordIslandSnapshot(islandId, snapshotNo, storagePath, reason, checksum, sizeBytes, nodeId);
    }
    CompletableFuture<String> requestIslandSaveResult(UUID islandId, String reason);
    CompletableFuture<Void> requestIslandSnapshot(UUID islandId, String reason);
    CompletableFuture<String> requestIslandSnapshotResult(UUID islandId, String reason);
    CompletableFuture<Void> restoreIslandSnapshot(UUID islandId, long snapshotNo);
    CompletableFuture<String> restoreIslandSnapshotResult(UUID islandId, long snapshotNo);
    CompletableFuture<String> rollbackIslandSnapshotResult(UUID islandId, long snapshotNo);
    CompletableFuture<String> listIslandLogs(UUID islandId, int limit);
    CompletableFuture<String> islandBank(UUID islandId);
    CompletableFuture<String> depositIslandBank(UUID islandId, UUID actorUuid, String amount);
    CompletableFuture<String> withdrawIslandBank(UUID islandId, UUID actorUuid, String amount);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid);
    CompletableFuture<RouteTicket> createHomeTicket(UUID playerUuid, String homeName);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, UUID targetIslandId);
    CompletableFuture<RouteTicket> createVisitTicket(UUID visitorUuid, String islandName);
    CompletableFuture<RouteTicket> createVisitTicketForOwner(UUID visitorUuid, UUID ownerUuid);
    CompletableFuture<RouteTicket> createRandomVisitTicket(UUID visitorUuid);
    CompletableFuture<RouteTicket> createWarpTicket(UUID playerUuid, UUID islandId, String warpName);
    CompletableFuture<RouteTicket> createMigrationReturnTicket(UUID playerUuid, UUID islandId, String targetNode, double localX, double localY, double localZ, float yaw, float pitch);
    CompletableFuture<Void> publishRouteSession(RouteTicket ticket);
    CompletableFuture<String> publishRouteSessionResult(RouteTicket ticket);
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
    CompletableFuture<String> activateIsland(UUID islandId);
    CompletableFuture<String> activateIslandResult(UUID islandId);
    CompletableFuture<String> deactivateIsland(UUID islandId);
    CompletableFuture<String> deactivateIslandResult(UUID islandId);
    CompletableFuture<String> migrateIsland(UUID islandId, String targetNode);
    CompletableFuture<String> migrateIslandResult(UUID islandId, String targetNode);
    CompletableFuture<String> quarantineIsland(UUID islandId, String reason);
    CompletableFuture<String> quarantineIslandResult(UUID islandId, String reason);
    CompletableFuture<String> adminIslandInfo(UUID lookupUuid);
    CompletableFuture<String> adminIslandWhere(UUID islandId);
    CompletableFuture<RouteTicket> adminIslandTeleport(UUID playerUuid, UUID islandId);
    CompletableFuture<String> adminDeleteIsland(UUID islandId);
    CompletableFuture<String> adminDeleteIslandResult(UUID islandId);
    default CompletableFuture<String> deleteIslandResult(UUID islandId) {
        return adminDeleteIslandResult(islandId);
    }
    CompletableFuture<String> repairIsland(UUID islandId, String reason);
    CompletableFuture<String> repairIslandResult(UUID islandId, String reason);
    CompletableFuture<String> debugRoutes(UUID playerUuid);
    CompletableFuture<String> routeTicket(UUID ticketId);
    CompletableFuture<String> routeTicketForPlayer(UUID playerUuid);
    CompletableFuture<String> clearRoute(UUID playerUuid, UUID ticketId);
    CompletableFuture<String> clearRouteResult(UUID playerUuid, UUID ticketId);
    CompletableFuture<String> clearRoute(UUID playerUuid, UUID ticketId, String reason);
    CompletableFuture<String> clearRouteResult(UUID playerUuid, UUID ticketId, String reason);
    CompletableFuture<String> listEvents();
    CompletableFuture<String> listEvents(int limit);
    CompletableFuture<String> listEventsSince(long sinceSeq, int limit);
    CompletableFuture<String> listAuditLogs();
    CompletableFuture<String> listAuditLogs(int limit);
    CompletableFuture<String> metrics();
    CompletableFuture<String> coreConfig();
    CompletableFuture<String> storageStatus();
    CompletableFuture<String> clearCache();
    CompletableFuture<String> clearCacheResult();
    CompletableFuture<String> reload();
    CompletableFuture<String> reloadResult();
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
    CompletableFuture<String> playerInfo(UUID playerUuid);
    CompletableFuture<String> playerInfoByName(String lastName);
    CompletableFuture<String> touchPlayerProfile(UUID playerUuid, String lastName);
    CompletableFuture<String> touchPlayerProfile(UUID playerUuid, String lastName, String locale);
    CompletableFuture<String> setPlayerLocale(UUID playerUuid, String locale);
    CompletableFuture<String> setPlayerIsland(UUID playerUuid, UUID islandId);
    CompletableFuture<String> clearPlayerIsland(UUID playerUuid);
    CompletableFuture<String> listTemplates();
    CompletableFuture<String> upsertTemplate(String templateId, String displayName, boolean enabled, String minNodeVersion);
    CompletableFuture<String> enableTemplate(String templateId);
    CompletableFuture<String> disableTemplate(String templateId);
    CompletableFuture<List<IslandJob>> claimJobs(String nodeId, List<IslandJobType> supportedTypes, int maxJobs);
    CompletableFuture<String> listJobs();
    CompletableFuture<String> retryJob(UUID jobId);
    CompletableFuture<String> retryJobResult(UUID jobId);
    CompletableFuture<String> cancelJob(UUID jobId);
    CompletableFuture<String> cancelJobResult(UUID jobId);
    CompletableFuture<String> recoverJobs(String nodeId, long minIdleMillis, int maxJobs);
    CompletableFuture<String> recoverJobsResult(String nodeId, long minIdleMillis, int maxJobs);
    CompletableFuture<Void> completeJob(String nodeId, UUID jobId);
    CompletableFuture<Void> completeJob(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<String> completeJobResult(String nodeId, UUID jobId, Map<String, String> payload);
    CompletableFuture<Void> failJob(String nodeId, UUID jobId, String errorMessage);
    CompletableFuture<String> failJobResult(String nodeId, UUID jobId, String errorMessage);
    CompletableFuture<Void> publishHeartbeat(NodeHeartbeatRequest request);
    CompletableFuture<String> publishHeartbeatResult(NodeHeartbeatRequest request);
}
