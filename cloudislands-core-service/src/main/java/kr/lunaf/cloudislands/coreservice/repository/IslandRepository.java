package kr.lunaf.cloudislands.coreservice.repository;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;

public interface IslandRepository {
    Optional<IslandSnapshot> findById(UUID islandId);
    Optional<IslandSnapshot> findByOwner(UUID ownerUuid);
    Optional<IslandSnapshot> findByName(String name);
    Optional<String> templateId(UUID islandId);
    IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name);
    void updateStats(UUID islandId, int size, long level, String worth);
    boolean markDeleted(UUID islandId, UUID requesterUuid);
    boolean transferOwnership(UUID islandId, UUID currentOwnerUuid, UUID newOwnerUuid);
    void createOwnerMember(UUID islandId, UUID ownerUuid);
    void createRuntime(UUID islandId, String state);
}
