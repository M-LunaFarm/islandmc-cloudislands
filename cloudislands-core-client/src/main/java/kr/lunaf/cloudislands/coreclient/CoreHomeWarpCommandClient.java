package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreHomeWarpCommandClient implements HomeWarpCommandClient {
    private final CoreApiClient delegate;

    public CoreHomeWarpCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<HomeWarpActionView> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireLocation(location);
        return delegate.setIslandHomeResult(islandId, actorUuid, normalizeName(name), location)
            .thenApply(body -> actionResult(body, "HOME_SET"));
    }

    @Override
    public CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireLocation(location);
        return delegate.setIslandWarpResult(islandId, actorUuid, normalizeName(name), location, publicAccess)
            .thenApply(body -> actionResult(body, "WARP_SET"));
    }

    @Override
    public CompletableFuture<HomeWarpActionView> deleteWarp(UUID islandId, UUID actorUuid, String name) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.deleteIslandWarpResult(islandId, actorUuid, normalizeName(name))
            .thenApply(body -> actionResult(body, "WARP_DELETED"));
    }

    @Override
    public CompletableFuture<HomeWarpActionView> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.setIslandWarpPublicAccessResult(islandId, actorUuid, normalizeName(name), publicAccess)
            .thenApply(body -> actionResult(body, publicAccess ? "WARP_PUBLIC" : "WARP_PRIVATE"));
    }

    private static HomeWarpActionView actionResult(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        String code = SimpleJson.text(root.get("code"));
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new HomeWarpActionView(accepted, code);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requireLocation(IslandLocation location) {
        if (location == null) {
            throw new IllegalArgumentException("location is required");
        }
    }

    private static String normalizeName(String name) {
        return name == null || name.isBlank() ? "default" : name;
    }
}
