package kr.lunaf.cloudislands.api.service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.api.model.IslandActionResult;
import kr.lunaf.cloudislands.api.model.IslandBankChangeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandChatResult;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandInviteActionResult;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLevelSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.api.upgrade.UpgradePurchaseSnapshot;

public interface IslandCommandService {
    CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid);
    CompletableFuture<CreateIslandResult> createIsland(UUID ownerUuid, String templateId);
    CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId);
    CompletableFuture<Void> resetIsland(UUID islandId, UUID actorUuid, String reason);
    CompletableFuture<IslandActionResult> resetIslandResult(UUID islandId, UUID actorUuid, String reason);
    CompletableFuture<Void> invite(UUID islandId, UUID inviterUuid, UUID targetUuid);
    CompletableFuture<IslandInviteSnapshot> inviteResult(UUID islandId, UUID inviterUuid, UUID targetUuid);
    CompletableFuture<Void> acceptInvite(UUID inviteId, UUID playerUuid);
    CompletableFuture<IslandInviteActionResult> acceptInviteResult(UUID inviteId, UUID playerUuid);
    CompletableFuture<Void> declineInvite(UUID inviteId, UUID playerUuid);
    CompletableFuture<IslandInviteActionResult> declineInviteResult(UUID inviteId, UUID playerUuid);
    CompletableFuture<Void> acceptInviteFromIsland(UUID playerUuid, UUID islandId);
    CompletableFuture<IslandInviteActionResult> acceptInviteFromIslandResult(UUID playerUuid, UUID islandId);
    CompletableFuture<Void> declineInviteFromIsland(UUID playerUuid, UUID islandId);
    CompletableFuture<IslandInviteActionResult> declineInviteFromIslandResult(UUID playerUuid, UUID islandId);
    CompletableFuture<Void> acceptInviteFromPlayer(UUID playerUuid, UUID inviterUuid);
    CompletableFuture<IslandInviteActionResult> acceptInviteFromPlayerResult(UUID playerUuid, UUID inviterUuid);
    CompletableFuture<Void> declineInviteFromPlayer(UUID playerUuid, UUID inviterUuid);
    CompletableFuture<IslandInviteActionResult> declineInviteFromPlayerResult(UUID playerUuid, UUID inviterUuid);
    CompletableFuture<Void> banVisitor(UUID islandId, UUID actorUuid, UUID targetUuid, String reason);
    CompletableFuture<IslandActionResult> banVisitorResult(UUID islandId, UUID actorUuid, UUID targetUuid, String reason);
    CompletableFuture<Void> pardonVisitor(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<IslandActionResult> pardonVisitorResult(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<Void> kick(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<IslandActionResult> kickResult(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<Void> trustPlayer(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<IslandActionResult> trustPlayerResult(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<Void> untrustPlayer(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<IslandActionResult> untrustPlayerResult(UUID islandId, UUID actorUuid, UUID targetUuid);
    default CompletableFuture<Void> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, RoleId roleId) {
        return setRole(islandId, actorUuid, targetUuid, roleId == null ? "" : roleId.value());
    }
    default CompletableFuture<IslandActionResult> setRoleResult(UUID islandId, UUID actorUuid, UUID targetUuid, RoleId roleId) {
        return setRoleResult(islandId, actorUuid, targetUuid, roleId == null ? "" : roleId.value());
    }
    default CompletableFuture<Void> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey) {
        return setRoleResult(islandId, actorUuid, targetUuid, roleKey).thenApply(_result -> null);
    }
    CompletableFuture<IslandActionResult> setRoleResult(UUID islandId, UUID actorUuid, UUID targetUuid, String roleKey);
    default CompletableFuture<Void> setRole(UUID islandId, UUID actorUuid, UUID targetUuid, IslandRole role) {
        return setRole(islandId, actorUuid, targetUuid, legacyRoleKey(role));
    }
    default CompletableFuture<IslandActionResult> setRoleResult(UUID islandId, UUID actorUuid, UUID targetUuid, IslandRole role) {
        return setRoleResult(islandId, actorUuid, targetUuid, legacyRoleKey(role));
    }
    CompletableFuture<Void> transferOwnership(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<IslandActionResult> transferOwnershipResult(UUID islandId, UUID actorUuid, UUID targetUuid);
    CompletableFuture<Void> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    CompletableFuture<IslandActionResult> setFlagResult(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
    default CompletableFuture<Void> setPermission(UUID islandId, UUID actorUuid, RoleId roleId, IslandPermission permission, boolean allowed) {
        return setPermission(islandId, actorUuid, roleId == null ? "" : roleId.value(), permission, allowed);
    }
    default CompletableFuture<IslandActionResult> setPermissionResult(UUID islandId, UUID actorUuid, RoleId roleId, IslandPermission permission, boolean allowed) {
        return setPermissionResult(islandId, actorUuid, roleId == null ? "" : roleId.value(), permission, allowed);
    }
    default CompletableFuture<Void> setPermission(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed) {
        return setPermissionResult(islandId, actorUuid, roleKey, permission, allowed).thenApply(_result -> null);
    }
    CompletableFuture<IslandActionResult> setPermissionResult(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed);
    default CompletableFuture<Void> setPermission(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) {
        return setPermission(islandId, actorUuid, legacyRoleKey(role), permission, allowed);
    }
    default CompletableFuture<IslandActionResult> setPermissionResult(UUID islandId, UUID actorUuid, IslandRole role, IslandPermission permission, boolean allowed) {
        return setPermissionResult(islandId, actorUuid, legacyRoleKey(role), permission, allowed);
    }
    default CompletableFuture<Void> upsertRole(UUID islandId, UUID actorUuid, RoleId roleId, int weight, String displayName) {
        return upsertRole(islandId, actorUuid, roleId == null ? "" : roleId.value(), weight, displayName);
    }
    default CompletableFuture<IslandRoleSnapshot> upsertRoleResult(UUID islandId, UUID actorUuid, RoleId roleId, int weight, String displayName) {
        return upsertRoleResult(islandId, actorUuid, roleId == null ? "" : roleId.value(), weight, displayName);
    }
    default CompletableFuture<Void> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName) {
        return upsertRoleResult(islandId, actorUuid, roleKey, weight, displayName).thenApply(_result -> null);
    }
    CompletableFuture<IslandRoleSnapshot> upsertRoleResult(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName);
    default CompletableFuture<Void> upsertRole(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName) {
        return upsertRole(islandId, actorUuid, legacyRoleKey(role), weight, displayName);
    }
    default CompletableFuture<IslandRoleSnapshot> upsertRoleResult(UUID islandId, UUID actorUuid, IslandRole role, int weight, String displayName) {
        return upsertRoleResult(islandId, actorUuid, legacyRoleKey(role), weight, displayName);
    }
    default CompletableFuture<Void> resetRole(UUID islandId, UUID actorUuid, RoleId roleId) {
        return resetRole(islandId, actorUuid, roleId == null ? "" : roleId.value());
    }
    default CompletableFuture<IslandActionResult> resetRoleResult(UUID islandId, UUID actorUuid, RoleId roleId) {
        return resetRoleResult(islandId, actorUuid, roleId == null ? "" : roleId.value());
    }
    default CompletableFuture<Void> resetRole(UUID islandId, UUID actorUuid, String roleKey) {
        return resetRoleResult(islandId, actorUuid, roleKey).thenApply(_result -> null);
    }
    CompletableFuture<IslandActionResult> resetRoleResult(UUID islandId, UUID actorUuid, String roleKey);
    default CompletableFuture<Void> resetRole(UUID islandId, UUID actorUuid, IslandRole role) {
        return resetRole(islandId, actorUuid, legacyRoleKey(role));
    }
    default CompletableFuture<IslandActionResult> resetRoleResult(UUID islandId, UUID actorUuid, IslandRole role) {
        return resetRoleResult(islandId, actorUuid, legacyRoleKey(role));
    }
    CompletableFuture<Void> setLocked(UUID islandId, UUID actorUuid, boolean locked);
    CompletableFuture<IslandActionResult> setLockedResult(UUID islandId, UUID actorUuid, boolean locked);
    CompletableFuture<Void> lockIsland(UUID islandId, UUID actorUuid);
    CompletableFuture<IslandActionResult> lockIslandResult(UUID islandId, UUID actorUuid);
    CompletableFuture<Void> unlockIsland(UUID islandId, UUID actorUuid);
    CompletableFuture<IslandActionResult> unlockIslandResult(UUID islandId, UUID actorUuid);
    CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, IslandLocation location);
    CompletableFuture<IslandActionResult> setHomeResult(UUID islandId, UUID actorUuid, IslandLocation location);
    CompletableFuture<Void> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<IslandActionResult> setHomeResult(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<Void> setBiome(UUID islandId, UUID actorUuid, String biomeKey);
    CompletableFuture<IslandActionResult> setBiomeResult(UUID islandId, UUID actorUuid, String biomeKey);
    CompletableFuture<Void> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value);
    CompletableFuture<IslandLimitSnapshot> setLimitResult(UUID islandId, UUID actorUuid, String limitKey, long value);
    CompletableFuture<Void> createWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<IslandActionResult> createWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location);
    CompletableFuture<Void> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess);
    CompletableFuture<IslandActionResult> setWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess);
    default CompletableFuture<IslandActionResult> setWarpResult(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, String category) {
        return setWarpResult(islandId, actorUuid, name, location, publicAccess);
    }
    CompletableFuture<Void> deleteWarp(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<IslandActionResult> deleteWarpResult(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<Void> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
    CompletableFuture<IslandActionResult> setWarpPublicAccessResult(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
    CompletableFuture<Void> publishWarp(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<IslandActionResult> publishWarpResult(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<Void> privatizeWarp(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<IslandActionResult> privatizeWarpResult(UUID islandId, UUID actorUuid, String name);
    CompletableFuture<Void> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess);
    CompletableFuture<IslandActionResult> setPublicAccessResult(UUID islandId, UUID actorUuid, boolean publicAccess);
    CompletableFuture<Void> publishIsland(UUID islandId, UUID actorUuid);
    CompletableFuture<IslandActionResult> publishIslandResult(UUID islandId, UUID actorUuid);
    CompletableFuture<Void> privatizeIsland(UUID islandId, UUID actorUuid);
    CompletableFuture<IslandActionResult> privatizeIslandResult(UUID islandId, UUID actorUuid);
    CompletableFuture<IslandLevelSnapshot> recalculateLevel(UUID islandId, UUID actorUuid);
    CompletableFuture<Void> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey);
    CompletableFuture<UpgradePurchaseSnapshot> purchaseUpgradeResult(UUID islandId, UUID actorUuid, String upgradeKey);
    CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey);
    CompletableFuture<java.util.Optional<IslandMissionSnapshot>> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey);
    default CompletableFuture<Void> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        return completeMissionResult(islandId, actorUuid, missionKey, kind).thenApply(_result -> null);
    }
    CompletableFuture<java.util.Optional<IslandMissionSnapshot>> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind);
    default CompletableFuture<Void> progressMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        return progressMissionResult(islandId, actorUuid, missionKey, kind, amount).thenApply(_result -> null);
    }
    CompletableFuture<java.util.Optional<IslandMissionSnapshot>> progressMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount);
    CompletableFuture<java.util.List<MissionProviderDefinitionSnapshot>> registerMissionProvider(String providerId, java.util.List<MissionProviderDefinitionSnapshot> definitions);
    default CompletableFuture<Void> completeChallenge(UUID islandId, UUID actorUuid, String challengeKey) {
        return completeMission(islandId, actorUuid, challengeKey, "CHALLENGE");
    }
    default CompletableFuture<java.util.Optional<IslandMissionSnapshot>> completeChallengeResult(UUID islandId, UUID actorUuid, String challengeKey) {
        return completeMissionResult(islandId, actorUuid, challengeKey, "CHALLENGE");
    }
    default CompletableFuture<Void> progressChallenge(UUID islandId, UUID actorUuid, String challengeKey, long amount) {
        return progressMission(islandId, actorUuid, challengeKey, "CHALLENGE", amount);
    }
    default CompletableFuture<java.util.Optional<IslandMissionSnapshot>> progressChallengeResult(UUID islandId, UUID actorUuid, String challengeKey, long amount) {
        return progressMissionResult(islandId, actorUuid, challengeKey, "CHALLENGE", amount);
    }
    CompletableFuture<Void> sendChat(UUID islandId, UUID actorUuid, String channel, String message);
    CompletableFuture<IslandChatResult> sendChatResult(UUID islandId, UUID actorUuid, String channel, String message);
    CompletableFuture<Void> sendIslandChat(UUID islandId, UUID actorUuid, String message);
    CompletableFuture<IslandChatResult> sendIslandChatResult(UUID islandId, UUID actorUuid, String message);
    CompletableFuture<Void> sendTeamChat(UUID islandId, UUID actorUuid, String message);
    CompletableFuture<IslandChatResult> sendTeamChatResult(UUID islandId, UUID actorUuid, String message);
    CompletableFuture<Void> depositBank(UUID islandId, UUID actorUuid, BigDecimal amount);
    CompletableFuture<IslandBankChangeSnapshot> depositBankResult(UUID islandId, UUID actorUuid, BigDecimal amount);
    CompletableFuture<Void> withdrawBank(UUID islandId, UUID actorUuid, BigDecimal amount);
    CompletableFuture<IslandBankChangeSnapshot> withdrawBankResult(UUID islandId, UUID actorUuid, BigDecimal amount);

    private static String legacyRoleKey(IslandRole role) {
        return role == null ? "" : role.name();
    }
}
