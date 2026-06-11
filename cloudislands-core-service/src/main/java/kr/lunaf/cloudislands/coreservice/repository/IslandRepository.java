package kr.lunaf.cloudislands.coreservice.repository;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;

public interface IslandRepository {
    Optional<IslandSnapshot> findById(UUID islandId);
    Optional<IslandSnapshot> findByOwner(UUID ownerUuid);
    IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name);
    boolean markDeleted(UUID islandId, UUID requesterUuid);
    boolean transferOwnership(UUID islandId, UUID currentOwnerUuid, UUID newOwnerUuid);
    void createOwnerMember(UUID islandId, UUID ownerUuid);
    void createRuntime(UUID islandId, String state);
}
