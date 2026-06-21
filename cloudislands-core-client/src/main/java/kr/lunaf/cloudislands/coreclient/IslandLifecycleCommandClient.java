package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;

public interface IslandLifecycleCommandClient {
    CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId);

    CompletableFuture<DeleteIslandResult> deleteIsland(UUID playerUuid, UUID islandId);

    CompletableFuture<IslandLifecycleActionView> resetIsland(UUID islandId, UUID actorUuid, String reason);
}
