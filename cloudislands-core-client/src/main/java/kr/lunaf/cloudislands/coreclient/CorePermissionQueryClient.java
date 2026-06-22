package kr.lunaf.cloudislands.coreclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CorePermissionQueryClient implements PermissionQueryClient {
    private final CoreApiClient delegate;

    public CorePermissionQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<PermissionAssignmentView>> permissions(UUID islandId) {
        requireIsland(islandId);
        return delegate.listIslandPermissions(islandId).thenApply(CorePermissionQueryClient::permissionViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.RoleView>> roles(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandRoles(delegate, islandId);
    }

    static List<PermissionAssignmentView> permissionViews(String body) {
        Map<?, ?> root = CoreJson.object(body);
        String version = text(root, "version");
        return entries(body).stream()
            .map(object -> {
                String permission = text(object, "permission");
                String role = text(object, "role");
                String playerUuid = text(object, "playerUuid");
                if (permission.isBlank() || (role.isBlank() && playerUuid.isBlank())) {
                    return null;
                }
                return new PermissionAssignmentView(role, playerUuid, permission, bool(object, "allowed"), version);
            })
            .filter(java.util.Objects::nonNull)
            .toList();
    }

    private static List<Map<?, ?>> entries(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<Map<?, ?>> entries = new ArrayList<>(CoreJson.objects(root, "rules"));
        if (entries.isEmpty()) {
            entries.addAll(CoreJson.objects(root, "permissions"));
        }
        entries.addAll(CoreJson.objects(root, "overrides"));
        return entries.isEmpty() ? CoreJson.entries(body) : entries;
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
    }
}
