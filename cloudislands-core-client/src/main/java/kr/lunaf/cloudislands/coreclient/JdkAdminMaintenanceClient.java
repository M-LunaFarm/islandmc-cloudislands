package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
        return core.postResultBody("/v1/admin/cache/clear", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminMaintenanceClient::result);
    }

    @Override
    public CompletableFuture<AdminMaintenanceResultView> reload() {
        return core.postResultBody("/v1/admin/reload", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminMaintenanceClient::result);
    }

    static AdminMaintenanceResultView result(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> error = CoreJson.objectValue(root, "error");
        String code = CoreJson.text(root, "code");
        if (code.isBlank()) {
            code = CoreJson.text(error, "code");
        }
        return new AdminMaintenanceResultView(
            CoreJson.bool(root, "reloaded"),
            CoreJson.number(root, "clearedSessions"),
            CoreJson.number(root, "clearedTickets"),
            CoreJson.number(root, "clearedRedisKeys"),
            code
        );
    }
}
