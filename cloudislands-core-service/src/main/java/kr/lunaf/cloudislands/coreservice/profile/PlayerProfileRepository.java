package kr.lunaf.cloudislands.coreservice.profile;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public interface PlayerProfileRepository {
    PlayerIslandProfile find(UUID playerUuid);
    Optional<PlayerIslandProfile> findByLastName(String lastName);
    PlayerIslandProfile touch(UUID playerUuid, String lastName);
    PlayerIslandProfile touch(UUID playerUuid, String lastName, String locale);
    PlayerIslandProfile setLocale(UUID playerUuid, String locale);
    PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId);
    PlayerIslandProfile clearPrimaryIsland(UUID playerUuid);
}
