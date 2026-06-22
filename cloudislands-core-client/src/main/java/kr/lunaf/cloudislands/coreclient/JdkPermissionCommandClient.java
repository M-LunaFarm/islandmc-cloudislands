package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandPermission;

public final class JdkPermissionCommandClient implements PermissionCommandClient {
    private final JdkCoreApiClient core;

    public JdkPermissionCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
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
                return setPermissionMutation(
                    request.islandId(),
                    request.actorUuid(),
                    change.roleId().value(),
                    change.permission(),
                    change.allowed(),
                    expectedVersion
                );
            });
        }
        return chain;
    }

    @Override
    public CompletableFuture<MutationResult<CoreGuiViews.RoleView>> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        String normalizedDisplayName = displayName == null || displayName.isBlank() ? normalizedRoleKey : displayName.trim();
        return core.postWithResultBody(
                "/v1/islands/roles/upsert",
                CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey, "weight", weight, "displayName", normalizedDisplayName)
            )
            .thenApply(JdkPermissionCommandClient::roleMutationResult);
    }

    @Override
    public CompletableFuture<MutationResult<CoreGuiViews.RoleView>> resetRole(UUID islandId, UUID actorUuid, String roleKey) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        return core.postWithResultBody(
                "/v1/islands/roles/reset",
                CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey)
            )
            .thenApply(JdkPermissionCommandClient::roleMutationResult);
    }

    @Override
    public CompletableFuture<PermissionActionView> setPermission(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed) {
        requirePermission(permission);
        return core.postWithResultBody(
                "/v1/islands/permissions/set",
                setPermissionPayload(islandId, actorUuid, normalizeRoleKey(roleKey), permission, allowed, "")
            )
            .thenApply(body -> permissionAction(body, "PERMISSION_SET"));
    }

    @Override
    public CompletableFuture<PermissionActionView> setPermissionOverride(UUID islandId, UUID actorUuid, UUID targetUuid, IslandPermission permission, boolean allowed) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
        requirePermission(permission);
        return core.postWithResultBody(
                "/v1/islands/permissions/overrides/set",
                CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "playerUuid", targetUuid, "permission", permission.name(), "allowed", allowed)
            )
            .thenApply(body -> permissionAction(body, "PERMISSION_OVERRIDE_SET"));
    }

    static MutationResult<PermissionMatrixView> mutationResult(String body) {
        CoreGuiViews.PermissionRulesView rules = CorePermissionJson.permissionRulesView(body);
        PermissionMatrixView view = new PermissionMatrixView(rules.version(), rules.rules());
        return new MutationResult<>(view, view.version(), true);
    }

    static MutationResult<CoreGuiViews.RoleView> roleMutationResult(String body) {
        CoreGuiViews.RoleView role = CorePermissionJson.roleView(body);
        return new MutationResult<>(role, "", true);
    }

    static PermissionActionView permissionAction(String body, String successCode) {
        Map<?, ?> root = CoreJson.object(body);
        return new PermissionActionView(CoreJson.accepted(root), CoreJson.code(root, successCode));
    }

    private static void requirePermission(IslandPermission permission) {
        if (permission == null) {
            throw new IllegalArgumentException("permission is required");
        }
    }

    private CompletableFuture<MutationResult<PermissionMatrixView>> setPermissionMutation(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed, String expectedVersion) {
        return core.postWithResultBody(
                "/v1/islands/permissions/set",
                setPermissionPayload(islandId, actorUuid, roleKey, permission, allowed, expectedVersion)
            )
            .thenApply(JdkPermissionCommandClient::mutationResult);
    }

    private static String setPermissionPayload(UUID islandId, UUID actorUuid, String roleKey, IslandPermission permission, boolean allowed, String expectedVersion) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        requirePermission(permission);
        String normalizedRoleKey = normalizeRoleKey(roleKey);
        return expectedVersion == null || expectedVersion.isBlank()
            ? CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey, "permission", permission.name(), "allowed", allowed)
            : CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "role", normalizedRoleKey, "roleKey", normalizedRoleKey, "permission", permission.name(), "allowed", allowed, "expectedVersion", expectedVersion);
    }

    private static String normalizeRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
