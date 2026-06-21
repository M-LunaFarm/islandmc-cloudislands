package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreAdminMaintenanceCommandClient implements AdminMaintenanceCommandClient {
    private final CoreApiClient delegate;

    public CoreAdminMaintenanceCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminMaintenanceResultView> clearCache() {
        return delegate.clearCache().thenApply(CoreAdminMaintenanceCommandClient::result);
    }

    @Override
    public CompletableFuture<AdminMaintenanceResultView> reload() {
        return delegate.reload().thenApply(CoreAdminMaintenanceCommandClient::result);
    }

    private static AdminMaintenanceResultView result(String body) {
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
