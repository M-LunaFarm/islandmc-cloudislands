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

    public CompletableFuture<String> saveSequentially(UUID islandId, UUID actorUuid, List<PermissionChange> changes, MutationRunner runner) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
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
