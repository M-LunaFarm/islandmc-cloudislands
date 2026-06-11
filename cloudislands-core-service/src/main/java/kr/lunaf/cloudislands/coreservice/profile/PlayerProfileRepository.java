package kr.lunaf.cloudislands.coreservice.profile;

import java.util.UUID;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public interface PlayerProfileRepository {
    PlayerIslandProfile find(UUID playerUuid);
    PlayerIslandProfile setPrimaryIsland(UUID playerUuid, UUID islandId);
    PlayerIslandProfile clearPrimaryIsland(UUID playerUuid);
}
