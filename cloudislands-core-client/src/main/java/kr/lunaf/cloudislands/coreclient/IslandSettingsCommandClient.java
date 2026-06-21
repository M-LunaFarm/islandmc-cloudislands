package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public interface IslandSettingsCommandClient {
    CompletableFuture<SettingsActionView> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess);

    CompletableFuture<SettingsActionView> setLocked(UUID islandId, UUID actorUuid, boolean locked);

    CompletableFuture<SettingsActionView> setName(UUID islandId, UUID actorUuid, String name);

    CompletableFuture<SettingsActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);
}
