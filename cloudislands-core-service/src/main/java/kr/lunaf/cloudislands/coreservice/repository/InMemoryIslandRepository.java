package kr.lunaf.cloudislands.coreservice.repository;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;

public final class InMemoryIslandRepository implements IslandRepository {
    private final Map<UUID, IslandSnapshot> byIslandId = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> byOwner = new ConcurrentHashMap<>();

    @Override
    public Optional<IslandSnapshot> findById(UUID islandId) {
        return Optional.ofNullable(byIslandId.get(islandId));
    }

    @Override
    public Optional<IslandSnapshot> findByOwner(UUID ownerUuid) {
        UUID islandId = byOwner.get(ownerUuid);
        return islandId == null ? Optional.empty() : Optional.ofNullable(byIslandId.get(islandId));
    }

    @Override
    public IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name) {
        if (byOwner.putIfAbsent(ownerUuid, islandId) != null) {
            throw new IllegalStateException("player already owns an island");
        }
        IslandSnapshot island = new IslandSnapshot(islandId, ownerUuid, name, IslandState.CREATING, 300, 0L, "0.00", false, Instant.now(), Instant.now());
        byIslandId.put(islandId, island);
        return island;
    }

    @Override
    public boolean markDeleted(UUID islandId, UUID requesterUuid) {
        IslandSnapshot island = byIslandId.get(islandId);
        if (island == null || !island.ownerUuid().equals(requesterUuid)) {
            return false;
        }
        byIslandId.remove(islandId);
        byOwner.remove(requesterUuid, islandId);
        return true;
    }

    @Override
    public void createOwnerMember(UUID islandId, UUID ownerUuid) {
    }

    @Override
    public void createRuntime(UUID islandId, String state) {
    }
}
