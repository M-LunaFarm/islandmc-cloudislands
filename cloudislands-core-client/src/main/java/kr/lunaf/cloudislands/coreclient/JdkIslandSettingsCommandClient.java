package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public final class JdkIslandSettingsCommandClient implements IslandSettingsCommandClient {
    private final JdkCoreApiClient core;

    public JdkIslandSettingsCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<SettingsActionView> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/access", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "publicAccess", publicAccess))
            .thenApply(body -> actionResult(body, publicAccess ? "PUBLIC_ACCESS_ENABLED" : "PUBLIC_ACCESS_DISABLED"));
    }

    @Override
    public CompletableFuture<SettingsActionView> setLocked(UUID islandId, UUID actorUuid, boolean locked) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/lock", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "locked", locked))
            .thenApply(body -> actionResult(body, locked ? "ISLAND_LOCKED" : "ISLAND_UNLOCKED"));
    }

    @Override
    public CompletableFuture<SettingsActionView> setName(UUID islandId, UUID actorUuid, String name) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/name", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "name", name == null ? "" : name))
            .thenApply(body -> actionResult(body, "ISLAND_RENAMED"));
    }

    @Override
    public CompletableFuture<SettingsActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireFlag(flag);
        return core.postWithResultBody("/v1/islands/flags/set", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "flag", flag.name(), "value", value == null ? "" : value))
            .thenApply(body -> actionResult(body, "FLAG_SET"));
    }

    private static SettingsActionView actionResult(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new SettingsActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requireFlag(IslandFlag flag) {
        if (flag == null) {
            throw new IllegalArgumentException("flag is required");
        }
    }
}
