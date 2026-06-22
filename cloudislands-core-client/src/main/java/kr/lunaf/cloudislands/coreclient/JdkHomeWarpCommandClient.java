package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;

public final class JdkHomeWarpCommandClient implements HomeWarpCommandClient {
    private final JdkCoreApiClient core;

    public JdkHomeWarpCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<HomeWarpActionView> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireLocation(location);
        return core.postWithResultBody("/v1/islands/homes/set", CoreJsonPayload.location(islandId, actorUuid, normalizeName(name), location))
            .thenApply(body -> actionResult(body, "HOME_SET"));
    }

    @Override
    public CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, String category) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requireLocation(location);
        return core.postWithResultBody("/v1/islands/warps/set", CoreJsonPayload.warp(islandId, actorUuid, normalizeName(name), category == null ? "" : category, location, publicAccess))
            .thenApply(body -> actionResult(body, "WARP_SET"));
    }

    @Override
    public CompletableFuture<HomeWarpActionView> deleteWarp(UUID islandId, UUID actorUuid, String name) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/warps/delete", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "name", normalizeName(name)))
            .thenApply(body -> actionResult(body, "WARP_DELETED"));
    }

    @Override
    public CompletableFuture<HomeWarpActionView> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/warps/access", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "name", normalizeName(name), "publicAccess", publicAccess))
            .thenApply(body -> actionResult(body, publicAccess ? "WARP_PUBLIC" : "WARP_PRIVATE"));
    }

    private static HomeWarpActionView actionResult(String body, String successCode) {
        Map<?, ?> root = CoreJson.actionObject(body, successCode);
        return new HomeWarpActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
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
