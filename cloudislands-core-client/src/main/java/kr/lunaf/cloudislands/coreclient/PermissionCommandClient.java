package kr.lunaf.cloudislands.coreclient;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public interface PermissionCommandClient {
    CompletableFuture<MutationResult<PermissionMatrixView>> updatePermissions(UpdatePermissionsRequest request);

    CompletableFuture<MutationResult<CoreGuiViews.RoleView>> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName);

    CompletableFuture<MutationResult<CoreGuiViews.RoleView>> resetRole(UUID islandId, UUID actorUuid, String roleKey);
}
