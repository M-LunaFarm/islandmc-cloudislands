package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;

public interface PermissionCommandClient {
    CompletableFuture<MutationResult<PermissionMatrixView>> updatePermissions(UpdatePermissionsRequest request);

    CompletableFuture<MutationResult<CoreGuiViews.RoleView>> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName);

    CompletableFuture<MutationResult<CoreGuiViews.RoleView>> resetRole(UUID islandId, UUID actorUuid, String roleKey);

    CompletableFuture<PermissionActionView> setPermission(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed);

    CompletableFuture<PermissionActionView> adminSetPermission(UUID islandId, String roleKey, IslandPermission permission, boolean allowed);

    CompletableFuture<PermissionActionView> adminResetPermissions(UUID islandId, String roleKey);

    CompletableFuture<PermissionActionView> setPermissionOverride(UUID islandId, UUID actorUuid, UUID targetUuid, IslandPermission permission, boolean allowed);
}
