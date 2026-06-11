package kr.lunaf.cloudislands.coreservice.repository;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;

public interface IslandRepository {
    Optional<IslandSnapshot> findByOwner(UUID ownerUuid);
    IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name);
    void createOwnerMember(UUID islandId, UUID ownerUuid);
    void createRuntime(UUID islandId, String state);
}
