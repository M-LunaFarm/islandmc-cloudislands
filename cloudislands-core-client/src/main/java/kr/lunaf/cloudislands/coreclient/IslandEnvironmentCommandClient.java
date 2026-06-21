package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public interface IslandEnvironmentCommandClient {
    CompletableFuture<EnvironmentActionView> setBiome(UUID islandId, UUID actorUuid, String biomeKey);

    CompletableFuture<EnvironmentActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value);

    CompletableFuture<EnvironmentActionView> setLimit(UUID islandId, UUID actorUuid, String limitKey, long value);
}
