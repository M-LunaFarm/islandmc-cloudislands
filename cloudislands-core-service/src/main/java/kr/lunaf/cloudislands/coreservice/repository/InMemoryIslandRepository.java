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
    private final Map<UUID, String> templates = new ConcurrentHashMap<>();

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
    public Optional<IslandSnapshot> findByName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return byIslandId.values().stream()
            .filter(island -> island.name().equalsIgnoreCase(name))
            .findFirst();
    }

    @Override
    public Optional<String> templateId(UUID islandId) {
        return Optional.ofNullable(templates.get(islandId));
    }

    @Override
    public IslandSnapshot createOwnedIsland(UUID islandId, UUID ownerUuid, String templateId, String name) {
        if (byOwner.putIfAbsent(ownerUuid, islandId) != null) {
            throw new IllegalStateException("player already owns an island");
        }
        IslandSnapshot island = new IslandSnapshot(islandId, ownerUuid, name, IslandState.CREATING, 300, 0L, "0.00", false, Instant.now(), Instant.now());
        byIslandId.put(islandId, island);
        templates.put(islandId, templateId == null || templateId.isBlank() ? "default" : templateId);
        return island;
    }

    @Override
    public void updateStats(UUID islandId, int size, long level, String worth) {
        IslandSnapshot island = byIslandId.get(islandId);
        if (island == null) {
            throw new IllegalStateException("island not found");
        }
        byIslandId.put(islandId, new IslandSnapshot(island.islandId(), island.ownerUuid(), island.name(), island.state(), size, level, worth, island.publicAccess(), island.createdAt(), Instant.now()));
    }

    @Override
    public boolean markDeleted(UUID islandId, UUID requesterUuid) {
        IslandSnapshot island = byIslandId.get(islandId);
        if (island == null || !island.ownerUuid().equals(requesterUuid)) {
            return false;
        }
        byIslandId.remove(islandId);
        templates.remove(islandId);
        byOwner.remove(requesterUuid, islandId);
        return true;
    }

    @Override
    public boolean transferOwnership(UUID islandId, UUID currentOwnerUuid, UUID newOwnerUuid) {
        IslandSnapshot island = byIslandId.get(islandId);
        if (island == null || !island.ownerUuid().equals(currentOwnerUuid) || byOwner.containsKey(newOwnerUuid)) {
            return false;
        }
        IslandSnapshot transferred = new IslandSnapshot(island.islandId(), newOwnerUuid, island.name(), island.state(), island.size(), island.level(), island.worth(), island.publicAccess(), island.createdAt(), Instant.now());
        byIslandId.put(islandId, transferred);
        byOwner.remove(currentOwnerUuid, islandId);
        byOwner.put(newOwnerUuid, islandId);
        return true;
    }

    @Override
    public void createOwnerMember(UUID islandId, UUID ownerUuid) {
    }

    @Override
    public void createRuntime(UUID islandId, String state) {
    }
}
