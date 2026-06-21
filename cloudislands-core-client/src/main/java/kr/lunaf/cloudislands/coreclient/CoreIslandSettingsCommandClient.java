package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreIslandSettingsCommandClient implements IslandSettingsCommandClient {
    private final CoreApiClient delegate;

    public CoreIslandSettingsCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<SettingsActionView> setPublicAccess(UUID islandId, UUID actorUuid, boolean publicAccess) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.setIslandPublicAccessResult(islandId, actorUuid, publicAccess)
            .thenApply(body -> actionResult(body, publicAccess ? "PUBLIC_ACCESS_ENABLED" : "PUBLIC_ACCESS_DISABLED"));
    }

    @Override
    public CompletableFuture<SettingsActionView> setLocked(UUID islandId, UUID actorUuid, boolean locked) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.setIslandLockedResult(islandId, actorUuid, locked)
            .thenApply(body -> actionResult(body, locked ? "ISLAND_LOCKED" : "ISLAND_UNLOCKED"));
    }

    @Override
    public CompletableFuture<SettingsActionView> setName(UUID islandId, UUID actorUuid, String name) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.setIslandNameResult(islandId, actorUuid, name == null ? "" : name)
            .thenApply(body -> actionResult(body, "ISLAND_RENAMED"));
    }

    @Override
    public CompletableFuture<SettingsActionView> setFlag(UUID islandId, UUID actorUuid, IslandFlag flag, String value) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireFlag(flag);
        return delegate.setIslandFlagResult(islandId, actorUuid, flag, value == null ? "" : value)
            .thenApply(body -> actionResult(body, "FLAG_SET"));
    }

    private static SettingsActionView actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new SettingsActionView(accepted, code);
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
