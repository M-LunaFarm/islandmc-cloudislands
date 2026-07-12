package kr.lunaf.cloudislands.coreservice.repository;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;

public interface IslandRepository {
    Optional<IslandSnapshot> findById(UUID islandId);
    Optional<IslandSnapshot> findByOwner(UUID ownerUuid);
    Optional<IslandSnapshot> findByName(String name);
    Optional<String> templateId(UUID islandId);
    IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name);
    void setState(UUID islandId, IslandState state);
    void updateStats(UUID islandId, int size, long level, String worth);
    void setPublicAccess(UUID islandId, boolean publicAccess);
    default boolean setPublicAccessResult(UUID islandId, boolean publicAccess) {
        setPublicAccess(islandId, publicAccess);
        return true;
    }
    default String setPublicAccessMutationResult(UUID islandId, boolean publicAccess) {
        return setPublicAccessResult(islandId, publicAccess) ? "APPLIED" : "ISLAND_NOT_FOUND";
    }
    boolean rename(UUID islandId, String name);
    default String renameResult(UUID islandId, String name) {
        return rename(islandId, name) ? "APPLIED" : "RENAME_DENIED";
    }
    boolean markDeleted(UUID islandId, UUID requesterUuid);
    Optional<IslandSnapshot> restoreDeleted(UUID islandId);
    boolean transferOwnership(UUID islandId, UUID currentOwnerUuid, UUID newOwnerUuid);
    void createOwnerMember(UUID islandId, UUID ownerUuid);
    void createRuntime(UUID islandId, String state);
}
