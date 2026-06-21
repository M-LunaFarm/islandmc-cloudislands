package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    public CompletableFuture<IslandLifecycleActionView> resetIsland(UUID islandId, UUID actorUuid, String reason) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedReason = reason == null || reason.isBlank() ? "player-reset" : reason.trim();
        return delegate.resetIslandResult(islandId, actorUuid, normalizedReason)
            .thenApply(body -> actionResult(body, "RESET_QUEUED"));
    }

    private static IslandLifecycleActionView actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new IslandLifecycleActionView(accepted, code);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
