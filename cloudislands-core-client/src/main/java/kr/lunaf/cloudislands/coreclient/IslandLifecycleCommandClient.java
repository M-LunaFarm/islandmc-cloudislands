package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;

public interface IslandLifecycleCommandClient {
    CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId);

    CompletableFuture<DeleteIslandResult> deleteIsland(UUID playerUuid, UUID islandId);

    CompletableFuture<IslandLifecycleActionView> resetIsland(UUID islandId, UUID actorUuid, String reason);

    CompletableFuture<IslandLifecycleActionView> activateIsland(UUID islandId);

    CompletableFuture<IslandLifecycleActionView> deactivateIsland(UUID islandId);

    CompletableFuture<IslandLifecycleActionView> migrateIsland(UUID islandId, String targetNode);

    CompletableFuture<IslandLifecycleActionView> saveIsland(UUID islandId, String reason);

    CompletableFuture<IslandLifecycleActionView> snapshotIsland(UUID islandId, String reason);

    CompletableFuture<IslandLifecycleActionView> restoreIslandSnapshot(UUID islandId, long snapshotNo);

    CompletableFuture<IslandLifecycleActionView> rollbackIslandSnapshot(UUID islandId, long snapshotNo);

    CompletableFuture<IslandLifecycleActionView> quarantineIsland(UUID islandId, String reason);

    CompletableFuture<IslandLifecycleActionView> repairIsland(UUID islandId, String reason);

    CompletableFuture<IslandLifecycleActionView> adminDeleteIsland(UUID islandId);
}
