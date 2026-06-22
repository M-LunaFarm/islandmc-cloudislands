package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

final class JdkAdminAuditClient implements AdminAuditQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminAuditClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<AdminAuditEntryView>> list(int limit) {
        return core.postResultBody("/v1/admin/audit/list", CoreJsonPayload.object("limit", Math.max(1, Math.min(limit, 500))))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminAuditClient::entries);
    }

    static List<AdminAuditEntryView> entries(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return CoreJson.objects(root, "audit").stream()
            .map(entry -> new AdminAuditEntryView(
                CoreJson.text(entry, "id"),
                CoreJson.text(entry, "actorUuid"),
                CoreJson.text(entry, "actorType"),
                CoreJson.text(entry, "action"),
                CoreJson.text(entry, "targetType"),
                CoreJson.text(entry, "targetId"),
                CoreJson.stringMap(CoreJson.objectValue(entry, "payload")),
                CoreJson.text(entry, "createdAt")
            ))
            .toList();
    }

}
