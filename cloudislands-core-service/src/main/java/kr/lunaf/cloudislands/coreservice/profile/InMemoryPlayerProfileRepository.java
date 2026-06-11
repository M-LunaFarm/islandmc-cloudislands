package kr.lunaf.cloudislands.coreservice.profile;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public final class InMemoryPlayerProfileRepository implements PlayerProfileRepository {
    private final Map<UUID, PlayerIslandProfile> profiles = new ConcurrentHashMap<>();

    @Override
    public PlayerIslandProfile find(UUID playerUuid) {
        return profiles.getOrDefault(playerUuid, new PlayerIslandProfile(playerUuid, "", Optional.empty(), Instant.EPOCH));
    }

    @Override
    public Optional<PlayerIslandProfile> findByLastName(String lastName) {
        if (lastName == null || lastName.isBlank()) {
            return Optional.empty();
        }
        return profiles.values().stream()
            .filter(profile -> profile.lastName().equalsIgnoreCase(lastName))
            .findFirst();
    }

    @Override
    public PlayerIslandProfile touch(UUID playerUuid, String lastName) {
        PlayerIslandProfile current = find(playerUuid);
        PlayerIslandProfile updated = new PlayerIslandProfile(playerUuid, lastName == null ? "" : lastName, current.primaryIslandId(), Instant.now());
        profiles.put(playerUuid, updated);
        return updated;
    }

    @Override
    public PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId) {
        PlayerIslandProfile current = find(playerUuid);
        PlayerIslandProfile updated = new PlayerIslandProfile(playerUuid, current.lastName(), Optional.of(islandId), current.lastSeenAt());
        profiles.put(playerUuid, updated);
        return updated;
    }

    @Override
    public PlayerIslandProfile clearPrimaryIsland(UUID playerUuid) {
        PlayerIslandProfile current = find(playerUuid);
        PlayerIslandProfile updated = new PlayerIslandProfile(playerUuid, current.lastName(), Optional.empty(), current.lastSeenAt());
        profiles.put(playerUuid, updated);
        return updated;
    }
}
