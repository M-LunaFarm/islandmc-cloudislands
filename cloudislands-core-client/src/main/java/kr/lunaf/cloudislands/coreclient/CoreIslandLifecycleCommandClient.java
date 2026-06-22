package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreIslandLifecycleCommandClient implements IslandLifecycleCommandClient {
    private final CoreApiClient delegate;

    public CoreIslandLifecycleCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId) {
        requireId(playerUuid, "playerUuid");
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        return delegate.createIsland(playerUuid, normalizedTemplateId);
    }

    @Override
    public CompletableFuture<DeleteIslandResult> deleteIsland(UUID playerUuid, UUID islandId) {
        requireId(playerUuid, "playerUuid");
        requireId(islandId, "islandId");
        return delegate.deleteIsland(playerUuid, islandId);
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> resetIsland(UUID islandId, UUID actorUuid, String reason) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedReason = reason == null || reason.isBlank() ? "player-reset" : reason.trim();
        return delegate.resetIslandResult(islandId, actorUuid, normalizedReason)
            .thenApply(body -> actionResult(body, "RESET_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> activateIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return delegate.activateIslandResult(islandId).thenApply(body -> actionResult(body, "ACTIVATING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> deactivateIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return delegate.deactivateIslandResult(islandId).thenApply(body -> actionResult(body, "SAVING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> migrateIsland(UUID islandId, String targetNode) {
        requireId(islandId, "islandId");
        return delegate.migrateIslandResult(islandId, targetNode == null ? "" : targetNode.trim()).thenApply(body -> actionResult(body, "MIGRATING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> saveIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return delegate.requestIslandSaveResult(islandId, reason == null || reason.isBlank() ? "ADMIN_SAVE" : reason.trim()).thenApply(body -> actionResult(body, "SNAPSHOT_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> snapshotIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return delegate.requestIslandSnapshotResult(islandId, reason == null || reason.isBlank() ? "ADMIN_MANUAL" : reason.trim()).thenApply(body -> actionResult(body, "SNAPSHOT_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> restoreIslandSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        return delegate.restoreIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionResult(body, "RESTORE_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> rollbackIslandSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        return delegate.rollbackIslandSnapshotResult(islandId, snapshotNo).thenApply(body -> actionResult(body, "RESTORE_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> quarantineIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return delegate.quarantineIslandResult(islandId, reason == null || reason.isBlank() ? "admin" : reason.trim()).thenApply(body -> actionResult(body, "QUARANTINED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> repairIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return delegate.repairIslandResult(islandId, reason == null || reason.isBlank() ? "admin" : reason.trim()).thenApply(body -> actionResult(body, "REPAIRED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> adminDeleteIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return delegate.adminDeleteIslandResult(islandId).thenApply(body -> actionResult(body, "DELETED", islandId));
    }

    static IslandLifecycleActionView actionResult(String body, String successCode, UUID fallbackIslandId) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> error = SimpleJson.object(root.get("error"));
        boolean accepted = error.isEmpty()
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = CoreJson.text(root, "code");
        if (code.isBlank()) {
            code = SimpleJson.text(error.get("code"));
        }
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        String islandId = CoreJson.text(root, "islandId");
        if (islandId.isBlank() && fallbackIslandId != null) {
            islandId = fallbackIslandId.toString();
        }
        return new IslandLifecycleActionView(
            accepted,
            code,
            islandId,
            CoreJson.number(root, "snapshotNo"),
            CoreJson.text(root, "storagePath")
        );
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
