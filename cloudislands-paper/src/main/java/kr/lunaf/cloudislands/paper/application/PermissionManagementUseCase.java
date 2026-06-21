package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.RoleView;
import kr.lunaf.cloudislands.coreclient.CorePermissionCommandClient;
import kr.lunaf.cloudislands.coreclient.MutationResult;
import kr.lunaf.cloudislands.coreclient.PermissionCommandClient;
import kr.lunaf.cloudislands.coreclient.PermissionMatrixView;
import kr.lunaf.cloudislands.coreclient.UpdatePermissionsRequest;

public final class PermissionManagementUseCase {
    private final CoreApiClient coreApiClient;
    private final PermissionCommandClient permissionCommands;

    public PermissionManagementUseCase(CoreApiClient coreApiClient) {
        this(coreApiClient, new CorePermissionCommandClient(coreApiClient));
    }

    PermissionManagementUseCase(CoreApiClient coreApiClient, PermissionCommandClient permissionCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (permissionCommands == null) {
            throw new IllegalArgumentException("permissionCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.permissionCommands = permissionCommands;
    }

    public CompletableFuture<String> listPermissions(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandPermissions(islandId);
    }

    public CompletableFuture<List<PermissionView>> listPermissionViews(UUID islandId) {
        return listPermissions(islandId).thenApply(PermissionManagementUseCase::permissionViews);
    }

    public CompletableFuture<String> listRoles(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandRoles(islandId);
    }

    public CompletableFuture<List<RoleView>> listRoleViews(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandRoles(coreApiClient, islandId);
    }

    public CompletableFuture<String> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        String normalizedRoleKey = RoleId.of(roleKey).value();
        String normalizedDisplayName = displayName == null || displayName.isBlank() ? normalizedRoleKey : displayName.trim();
        return runner.mutate("island.role.upsert", () -> coreApiClient.upsertIslandRole(islandId, actorUuid, normalizedRoleKey, weight, normalizedDisplayName));
    }

    public CompletableFuture<MutationResult<RoleView>> upsertRoleTyped(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        return runner.mutate("island.role.upsert", () -> permissionCommands.upsertRole(islandId, actorUuid, roleKey, weight, displayName));
    }

    public CompletableFuture<String> resetRole(UUID islandId, UUID actorUuid, String roleKey, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        String normalizedRoleKey = RoleId.of(roleKey).value();
        return runner.mutateIdempotent("island.role.reset", () -> coreApiClient.resetIslandRole(islandId, actorUuid, normalizedRoleKey));
    }

    public CompletableFuture<MutationResult<RoleView>> resetRoleTyped(UUID islandId, UUID actorUuid, String roleKey, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.role.reset", () -> permissionCommands.resetRole(islandId, actorUuid, roleKey));
    }

    public CompletableFuture<String> setPermission(UUID islandId, UUID actorUuid, PermissionChange change, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        if (change == null) {
            throw new IllegalArgumentException("change is required");
        }
        return runner.mutate("island.permission.set", () -> coreApiClient.setIslandPermissionResult(islandId, actorUuid, change.roleKey(), change.permission(), change.allowed()));
    }

    public CompletableFuture<PermissionActionResult> setPermissionAction(UUID islandId, UUID actorUuid, PermissionChange change, MutationRunner runner) {
        return setPermission(islandId, actorUuid, change, runner)
            .thenApply(body -> permissionAction(body, "PERMISSION_SET"));
    }

    public CompletableFuture<String> setPermissionOverride(UUID islandId, UUID actorUuid, UUID targetUuid, IslandPermission permission, boolean allowed, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid is required");
        }
        if (permission == null) {
            throw new IllegalArgumentException("permission is required");
        }
        requireRunner(runner);
        return runner.mutate("island.permission.override.set", () -> coreApiClient.setIslandPermissionOverride(islandId, actorUuid, targetUuid, permission, allowed));
    }

    public CompletableFuture<PermissionActionResult> setPermissionOverrideAction(UUID islandId, UUID actorUuid, UUID targetUuid, IslandPermission permission, boolean allowed, MutationRunner runner) {
        return setPermissionOverride(islandId, actorUuid, targetUuid, permission, allowed, runner)
            .thenApply(body -> permissionAction(body, "PERMISSION_OVERRIDE_SET"));
    }

    public CompletableFuture<String> saveSequentially(UUID islandId, UUID actorUuid, List<PermissionChange> changes, MutationRunner runner) {
        requireRunner(runner);
        return runner.mutate("island.permission.batch-save", () -> saveSequentiallyTyped(islandId, actorUuid, changes).thenApply(MutationResult::version));
    }

    public CompletableFuture<MutationResult<PermissionMatrixView>> saveSequentiallyTyped(UUID islandId, UUID actorUuid, List<PermissionChange> changes) {
        requireIsland(islandId);
        requireActor(actorUuid);
        List<UpdatePermissionsRequest.Change> typedChanges = (changes == null ? List.<PermissionChange>of() : changes).stream()
            .map(change -> new UpdatePermissionsRequest.Change(change.roleKey(), change.permission(), change.allowed(), change.expectedVersion()))
            .toList();
        return permissionCommands.updatePermissions(new UpdatePermissionsRequest(islandId, actorUuid, typedChanges));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static void requireIdempotentRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    private static List<PermissionView> permissionViews(String body) {
        return entries(body).stream()
            .map(object -> {
                String permission = text(object, "permission");
                String role = text(object, "role");
                String playerUuid = text(object, "playerUuid");
                if (permission.isBlank() || (role.isBlank() && playerUuid.isBlank())) {
                    return null;
                }
                return new PermissionView(role, playerUuid, permission, bool(object, "allowed"));
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private static PermissionActionResult permissionAction(String body, String successCode) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = bool(root, "accepted", true);
        accepted = accepted && !root.containsKey("error") && !Boolean.FALSE.equals(root.get("applied"));
        String code = text(root, "code");
        if (code.isBlank()) {
            code = accepted ? successCode : "FAILED";
        }
        return new PermissionActionResult(accepted, code);
    }

    private static List<Map<?, ?>> entries(String body) {
        Object parsed = SimpleJson.parse(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        return bool(object, key, false);
    }

    private static boolean bool(Map<?, ?> object, String key, boolean fallback) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

    @FunctionalInterface
    public interface MutationRunner {
        <T> CompletableFuture<T> mutate(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
    }

    public record PermissionChange(String roleKey, IslandPermission permission, boolean allowed, String expectedVersion) {
        public PermissionChange {
            roleKey = RoleId.of(roleKey).value();
            if (permission == null) {
                throw new IllegalArgumentException("permission is required");
            }
            expectedVersion = expectedVersion == null ? "" : expectedVersion.trim();
        }

        public String key() {
            return roleKey + ":" + permission.name();
        }
    }

    public record PermissionView(String role, String playerUuid, String permission, boolean allowed) {
        public PermissionView {
            role = role == null ? "" : role;
            playerUuid = playerUuid == null ? "" : playerUuid;
            permission = permission == null ? "" : permission;
        }
    }

    public record PermissionActionResult(boolean accepted, String code) {
        public PermissionActionResult {
            code = code == null ? "" : code;
        }
    }
}
