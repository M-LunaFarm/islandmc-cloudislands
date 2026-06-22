package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkAdminMaintenanceClient implements AdminMaintenanceCommandClient {
    private final JdkCoreApiClient core;

    JdkAdminMaintenanceClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminMaintenanceResultView> clearCache() {
        return core.postWithResultBody("/v1/admin/cache/clear", "{}")
            .thenApply(JdkAdminMaintenanceClient::result);
    }

    @Override
    public CompletableFuture<AdminMaintenanceResultView> reload() {
        return core.postWithResultBody("/v1/admin/reload", "{}")
            .thenApply(JdkAdminMaintenanceClient::result);
    }

    static AdminMaintenanceResultView result(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> error = SimpleJson.object(root.get("error"));
        String code = text(root, "code");
        if (code.isBlank()) {
            code = text(error, "code");
        }
        return new AdminMaintenanceResultView(
            bool(root, "reloaded"),
            number(root, "clearedSessions"),
            number(root, "clearedTickets"),
            number(root, "clearedRedisKeys"),
            code
        );
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static long number(Map<?, ?> object, String key) {
        return SimpleJson.number(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(SimpleJson.text(value));
    }
}
