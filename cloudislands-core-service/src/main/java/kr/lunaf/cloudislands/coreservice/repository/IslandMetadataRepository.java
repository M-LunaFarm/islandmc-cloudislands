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
    void upsertMember(UUID islandId, UUID playerUuid, IslandRole role);
    void removeMember(UUID islandId, UUID playerUuid);
    IslandInviteSnapshot createInvite(UUID islandId, UUID inviterUuid, UUID targetUuid);
    List<IslandInviteSnapshot> pendingInvites(UUID targetUuid);
    boolean acceptInvite(UUID inviteId, UUID playerUuid);
    boolean declineInvite(UUID inviteId, UUID playerUuid);
    boolean isBanned(UUID islandId, UUID playerUuid);
    List<IslandBanSnapshot> bans(UUID islandId);
    void banVisitor(UUID islandId, UUID actorUuid, UUID playerUuid, String reason);
    void pardonVisitor(UUID islandId, UUID playerUuid);
    boolean isLocked(UUID islandId);
    void setLocked(UUID islandId, boolean locked);
    IslandFlagsSnapshot flags(UUID islandId);
    void setFlag(UUID islandId, IslandFlag flag, String value);
    IslandBiomeSnapshot biome(UUID islandId);
    void setBiome(UUID islandId, String biomeKey, UUID updatedBy);
    List<IslandHomeSnapshot> homes(UUID islandId);
    java.util.Optional<IslandHomeSnapshot> home(UUID islandId, String name);
    void upsertHome(UUID islandId, String name, IslandLocation location, UUID createdBy);
    List<IslandWarpSnapshot> warps(UUID islandId);
    Optional<IslandWarpSnapshot> warp(UUID islandId, String name);
    void upsertWarp(UUID islandId, String name, IslandLocation location, boolean publicAccess, UUID createdBy);
    void deleteWarp(UUID islandId, String name);
    boolean isPublicAccess(UUID islandId);
    void setPublicAccess(UUID islandId, boolean publicAccess);
    List<UUID> publicIslandIds(int limit);
}
