package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;

public final class CorePermissionCommandClient implements PermissionCommandClient {
    private final CoreApiClient delegate;

    public CorePermissionCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<MutationResult<PermissionMatrixView>> updatePermissions(UpdatePermissionsRequest request) {
        if (request == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("request is required"));
        }
        List<UpdatePermissionsRequest.Change> changes = request.changes();
        if (changes.isEmpty()) {
            PermissionMatrixView empty = new PermissionMatrixView("", List.of());
            return CompletableFuture.completedFuture(new MutationResult<>(empty, empty.version(), false));
        }
        CompletableFuture<MutationResult<PermissionMatrixView>> chain = CompletableFuture.completedFuture(
            new MutationResult<>(new PermissionMatrixView("", List.of()), "", false)
        );
        for (UpdatePermissionsRequest.Change change : changes) {
            chain = chain.thenCompose(previous -> {
                String expectedVersion = previous.version().isBlank() ? change.expectedVersion() : previous.version();
                return delegate.setIslandPermissionResult(
                    request.islandId(),
                    request.actorUuid(),
                    change.roleId().value(),
                    change.permission(),
                    change.allowed(),
                    expectedVersion
                ).thenApply(CorePermissionCommandClient::mutationResult);
            });
        }
        return chain;
    }

    @Override
    public CompletableFuture<MutationResult<CoreGuiViews.RoleView>> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName) {
        String normalizedRoleKey = RoleId.of(roleKey).value();
        String normalizedDisplayName = displayName == null || displayName.isBlank() ? normalizedRoleKey : displayName.trim();
        return delegate.upsertIslandRole(islandId, actorUuid, normalizedRoleKey, weight, normalizedDisplayName)
            .thenApply(CorePermissionCommandClient::roleMutationResult);
    }

    @Override
    public CompletableFuture<MutationResult<CoreGuiViews.RoleView>> resetRole(UUID islandId, UUID actorUuid, String roleKey) {
        String normalizedRoleKey = RoleId.of(roleKey).value();
        return delegate.resetIslandRole(islandId, actorUuid, normalizedRoleKey)
            .thenApply(CorePermissionCommandClient::roleMutationResult);
    }

    @Override
    public CompletableFuture<PermissionActionView> setPermission(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed) {
        requirePermission(permission);
        String normalizedRoleKey = RoleId.of(roleKey).value();
        return delegate.setIslandPermissionResult(islandId, actorUuid, normalizedRoleKey, permission, allowed)
            .thenApply(body -> permissionAction(body, "PERMISSION_SET"));
    }

    @Override
    public CompletableFuture<PermissionActionView> setPermissionOverride(UUID islandId, UUID actorUuid, UUID targetUuid, IslandPermission permission, boolean allowed) {
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
        requirePermission(permission);
        return delegate.setIslandPermissionOverride(islandId, actorUuid, targetUuid, permission, allowed)
            .thenApply(body -> permissionAction(body, "PERMISSION_OVERRIDE_SET"));
    }

    private static MutationResult<PermissionMatrixView> mutationResult(String body) {
        CoreGuiViews.PermissionRulesView rules = CoreGuiViews.permissionRulesView(body);
        PermissionMatrixView view = new PermissionMatrixView(rules.version(), rules.rules());
        return new MutationResult<>(view, view.version(), true);
    }

    private static MutationResult<CoreGuiViews.RoleView> roleMutationResult(String body) {
        CoreGuiViews.RoleView role = CoreGuiViews.roleView(body);
        return new MutationResult<>(role, "", true);
    }

    private static PermissionActionView permissionAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new PermissionActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
    }

    private static void requirePermission(IslandPermission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission is required");
        }
    }
}
