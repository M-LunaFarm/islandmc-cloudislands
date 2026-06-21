package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.RoleId;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class PermissionManagementUseCase {
    private final CoreApiClient coreApiClient;

    public PermissionManagementUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    public CompletableFuture<String> listPermissions(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandPermissions(islandId);
    }

    public CompletableFuture<String> listRoles(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.listIslandRoles(islandId);
    }

    public CompletableFuture<String> upsertRole(UUID islandId, UUID actorUuid, String roleKey, int weight, String displayName, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        String normalizedRoleKey = RoleId.of(roleKey).value();
        String normalizedDisplayName = displayName == null || displayName.isBlank() ? normalizedRoleKey : displayName.trim();
        return runner.mutate("island.role.upsert", () -> coreApiClient.upsertIslandRole(islandId, actorUuid, normalizedRoleKey, weight, normalizedDisplayName));
    }

    public CompletableFuture<String> resetRole(UUID islandId, UUID actorUuid, String roleKey, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        String normalizedRoleKey = RoleId.of(roleKey).value();
        return runner.mutateIdempotent("island.role.reset", () -> coreApiClient.resetIslandRole(islandId, actorUuid, normalizedRoleKey));
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

    public CompletableFuture<String> saveSequentially(UUID islandId, UUID actorUuid, List<PermissionChange> changes, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireRunner(runner);
        CompletableFuture<String> chain = CompletableFuture.completedFuture("");
        for (PermissionChange change : changes == null ? List.<PermissionChange>of() : changes) {
            chain = chain.thenCompose(previousBody -> {
                String previousVersion = text(previousBody, "version");
                String expectedVersion = previousVersion.isBlank() ? change.expectedVersion() : previousVersion;
                return runner.mutate("island.permission.batch-save", () ->
                    coreApiClient.setIslandPermissionResult(islandId, actorUuid, change.roleKey(), change.permission(), change.allowed(), expectedVersion)
                );
            });
        }
        return chain;
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

    private static String text(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int start = json == null ? -1 : json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int valueStart = start + needle.length();
        int end = jsonStringEnd(json, valueStart);
        return end < 0 ? "" : unescape(json.substring(valueStart, end));
    }

    private static int jsonStringEnd(String value, int start) {
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                return index;
            }
        }
        return -1;
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaped) {
                if (current == '\\') {
                    escaped = true;
                } else {
                    builder.append(current);
                }
                continue;
            }
            switch (current) {
                case 'n' -> builder.append('\n');
                case 'r' -> builder.append('\r');
                case 't' -> builder.append('\t');
                case '"' -> builder.append('"');
                case '\\' -> builder.append('\\');
                default -> builder.append(current);
            }
            escaped = false;
        }
        return builder.toString();
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutate(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
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
}
