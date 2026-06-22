package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkIslandLifecycleCommandClient implements IslandLifecycleCommandClient {
    private final JdkCoreApiClient core;

    JdkIslandLifecycleCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<CreateIslandResult> createIsland(UUID playerUuid, String templateId) {
        requireId(playerUuid, "playerUuid");
        String normalizedTemplateId = templateId == null || templateId.isBlank() ? "default" : templateId.trim();
        return core.post("/v1/islands", JdkCoreApiClient.jsonObject("playerUuid", playerUuid, "templateId", normalizedTemplateId))
            .thenApply(JdkIslandLifecycleCommandClient::createIslandResult);
    }

    @Override
    public CompletableFuture<DeleteIslandResult> deleteIsland(UUID requesterUuid, UUID islandId) {
        requireId(requesterUuid, "requesterUuid");
        requireId(islandId, "islandId");
        return core.deleteWithResultBody("/v1/islands/" + islandId + "?requesterUuid=" + requesterUuid)
            .thenApply(body -> deleteIslandResult(body, islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> resetIsland(UUID islandId, UUID actorUuid, String reason) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/reset", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "reason", reason == null || reason.isBlank() ? "player-reset" : reason.trim()))
            .thenApply(body -> lifecycleAction(body, "RESET_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> saveIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/save", JdkCoreApiClient.jsonObject("islandId", islandId, "reason", lifecycleReason(reason, "ADMIN_SAVE")))
            .thenApply(body -> lifecycleAction(body, "SNAPSHOT_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> snapshotIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/snapshot", JdkCoreApiClient.jsonObject("islandId", islandId, "reason", lifecycleReason(reason, "ADMIN_MANUAL")))
            .thenApply(body -> lifecycleAction(body, "SNAPSHOT_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> restoreIslandSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/restore", JdkCoreApiClient.jsonObject("islandId", islandId, "snapshotNo", snapshotNo))
            .thenApply(body -> lifecycleAction(body, "RESTORE_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> rollbackIslandSnapshot(UUID islandId, long snapshotNo) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/rollback", JdkCoreApiClient.jsonObject("islandId", islandId, "snapshotNo", snapshotNo))
            .thenApply(body -> lifecycleAction(body, "RESTORE_QUEUED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> activateIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/activate", JdkCoreApiClient.jsonObject("islandId", islandId))
            .thenApply(body -> lifecycleAction(body, "ACTIVATING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> deactivateIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/deactivate", JdkCoreApiClient.jsonObject("islandId", islandId))
            .thenApply(body -> lifecycleAction(body, "SAVING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> migrateIsland(UUID islandId, String targetNode) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/migrate", JdkCoreApiClient.jsonObject("islandId", islandId, "targetNode", targetNode == null ? "" : targetNode.trim()))
            .thenApply(body -> lifecycleAction(body, "MIGRATING", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> quarantineIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/" + islandId + "/quarantine", JdkCoreApiClient.jsonObject("reason", lifecycleReason(reason, "admin")))
            .thenApply(body -> lifecycleAction(body, "QUARANTINED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> repairIsland(UUID islandId, String reason) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/" + islandId + "/repair", JdkCoreApiClient.jsonObject("reason", lifecycleReason(reason, "admin")))
            .thenApply(body -> lifecycleAction(body, "REPAIRED", islandId));
    }

    @Override
    public CompletableFuture<IslandLifecycleActionView> adminDeleteIsland(UUID islandId) {
        requireId(islandId, "islandId");
        return core.postWithResultBody("/v1/admin/islands/" + islandId + "/delete", "{}")
            .thenApply(body -> lifecycleAction(body, "DELETED", islandId));
    }

    private static String lifecycleReason(String reason, String fallback) {
        return reason == null || reason.isBlank() ? fallback : reason.trim();
    }

    private static IslandLifecycleActionView lifecycleAction(String body, String successCode, UUID fallbackIslandId) {
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

    private static CreateIslandResult createIslandResult(String body) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = bool(root, "accepted") || bool(root, "created");
        return new CreateIslandResult(
            accepted,
            code(root),
            null,
            CoreRouteJson.nestedRouteTicket(body == null ? "" : body, "ticket")
        );
    }

    private static DeleteIslandResult deleteIslandResult(String body, UUID fallbackIslandId) {
        Map<?, ?> root = CoreJson.object(body);
        return new DeleteIslandResult(
            CoreJson.accepted(root),
            code(root),
            uuid(root, "islandId", fallbackIslandId)
        );
    }

    private static String code(Map<?, ?> root) {
        String code = SimpleJson.text(root.get("code"));
        return code.isBlank() ? "FAILED" : code;
    }

    private static boolean bool(Map<?, ?> root, String key) {
        Object value = root.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }

    private static UUID uuid(Map<?, ?> root, String key, UUID fallback) {
        String value = SimpleJson.text(root.get(key));
        if (value.isBlank()) {
            return fallback;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
