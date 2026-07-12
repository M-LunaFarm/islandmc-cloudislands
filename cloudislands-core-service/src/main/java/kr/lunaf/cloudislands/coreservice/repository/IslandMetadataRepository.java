package kr.lunaf.cloudislands.coreservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public interface IslandMetadataRepository {
    List<IslandMemberSnapshot> members(UUID islandId);
    List<IslandMemberSnapshot> islandsForMember(UUID playerUuid);
    boolean isMember(UUID islandId, UUID playerUuid);
    void upsertMemberKey(UUID islandId, UUID playerUuid, String roleKey);
    default void upsertMemberKeyAndInitializePrimary(UUID islandId, UUID playerUuid, String roleKey) {
        upsertMemberKey(islandId, playerUuid, roleKey);
    }
    default String upsertMemberKeyAndInitializePrimary(UUID islandId, UUID playerUuid, String roleKey, long maxMembers, long maxRoleMembers) {
        upsertMemberKeyAndInitializePrimary(islandId, playerUuid, roleKey);
        return "APPLIED";
    }
    @Deprecated(forRemoval = false)
    default void upsertMember(UUID islandId, UUID playerUuid, IslandRole role) {
        upsertMemberKey(islandId, playerUuid, role == null ? "" : role.name());
    }
    @Deprecated(forRemoval = false)
    default void upsertMember(UUID islandId, UUID playerUuid, IslandRole role, java.time.Instant expiresAt) {
        upsertMemberKey(islandId, playerUuid, role == null ? "" : role.name(), expiresAt);
    }
    default void upsertMemberKey(UUID islandId, UUID playerUuid, String roleKey, java.time.Instant expiresAt) {
        upsertMemberKey(islandId, playerUuid, roleKey);
    }
    default String upsertMemberKeyWithRoleLimit(UUID islandId, UUID playerUuid, String roleKey, java.time.Instant expiresAt, long maxRoleMembers) {
        upsertMemberKey(islandId, playerUuid, roleKey, expiresAt);
        return "APPLIED";
    }
    void removeMember(UUID islandId, UUID playerUuid);
    default void removeMemberAndClearPrimary(UUID islandId, UUID playerUuid) {
        removeMember(islandId, playerUuid);
    }
    default boolean removeMemberAndClearPrimaryResult(UUID islandId, UUID playerUuid) {
        removeMemberAndClearPrimary(islandId, playerUuid);
        return true;
    }
    IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid);
    List<IslandInviteSnapshot> pendingInvites(UUID targetUuid);
    boolean acceptInvite(UUID inviteId, UUID playerUuid);
    default boolean acceptInvite(UUID inviteId, UUID playerUuid, long maxMembers) {
        return acceptInvite(inviteId, playerUuid);
    }
    default String acceptInviteResult(UUID inviteId, UUID playerUuid, long maxMembers) {
        return acceptInvite(inviteId, playerUuid, maxMembers) ? "APPLIED" : "INVITE_UNAVAILABLE";
    }
    boolean declineInvite(UUID inviteId, UUID playerUuid);
    default String declineInviteResult(UUID inviteId, UUID playerUuid) {
        return declineInvite(inviteId, playerUuid) ? "APPLIED" : "INVITE_UNAVAILABLE";
    }
    boolean isBanned(UUID islandId, UUID playerUuid);
    List<IslandBanSnapshot> bans(UUID islandId);
    void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason);
    default String banVisitorResult(UUID islandId, UUID actorUuid, UUID playerUuid, String reason) {
        banVisitor(islandId, actorUuid, playerUuid, reason);
        return "APPLIED";
    }
    void pardonVisitor(UUID islandId, UUID playerUuid);
    default String pardonVisitorResult(UUID islandId, UUID playerUuid) {
        pardonVisitor(islandId, playerUuid);
        return "APPLIED";
    }
    boolean isLocked(UUID islandId);
    void setLocked(UUID islandId, boolean locked);
    default boolean setLockedResult(UUID islandId, boolean locked) {
        setLocked(islandId, locked);
        return true;
    }
    default String setLockedMutationResult(UUID islandId, boolean locked) {
        return setLockedResult(islandId, locked) ? "APPLIED" : "ISLAND_NOT_FOUND";
    }
    IslandFlagsSnapshot flags(UUID islandId);
    void setFlag(UUID islandId, IslandFlag flag, String value);
    default String setFlagResult(UUID islandId, IslandFlag flag, String value) {
        setFlag(islandId, flag, value);
        return "APPLIED";
    }
    boolean resetFlags(UUID islandId);
    IslandBiomeSnapshot biome(UUID islandId);
    void setBiome(UUID islandId, String biomeKey, UUID updatedBy);
    List<IslandHomeSnapshot> homes(UUID islandId);
    java.util.Optional<IslandHomeSnapshot> home(UUID islandId, String name);
    void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy);
    default String upsertHomeWithLimit(UUID islandId, String name, IslandLocation location, UUID createdBy, long maxHomes) {
        upsertHome(islandId, name, location, createdBy);
        return "APPLIED";
    }
    List<IslandWarpSnapshot> warps(UUID islandId);
    List<IslandWarpSnapshot> publicWarps(int limit);
    List<IslandWarpSnapshot> publicWarps(int limit, String category, String query);
    Optional<IslandWarpSnapshot> warp(UUID islandId, String name);
    void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy);
    void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category);
    default String upsertWarpWithLimit(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy, String category, long maxWarps) {
        upsertWarp(islandId, name, location, publicAccess, createdBy, category);
        return "APPLIED";
    }
    void setWarpPublicAccess(UUID islandId, String name, boolean publicAccess);
    default boolean setWarpPublicAccessResult(UUID islandId, String name, boolean publicAccess) {
        setWarpPublicAccess(islandId, name, publicAccess);
        return true;
    }
    void deleteWarp(UUID islandId, String name);
    default boolean deleteWarpResult(UUID islandId, String name) {
        deleteWarp(islandId, name);
        return true;
    }
    boolean isPublicAccess(UUID islandId);
    void setPublicAccess(UUID islandId, boolean publicAccess);
    List<UUID> publicIslandIds(int limit);
    default List<UUID> publicIslandIdsPage(int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        return publicIslandIds(safeOffset + Math.max(0, limit)).stream()
            .skip(safeOffset)
            .limit(Math.max(0, limit))
            .toList();
    }
}
