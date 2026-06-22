package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

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
                stringMap(SimpleJson.object(entry.get("payload"))),
                CoreJson.text(entry, "createdAt")
            ))
            .toList();
    }

    private static Map<String, String> stringMap(Map<?, ?> object) {
        return object.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            entry -> SimpleJson.text(entry.getKey()),
            entry -> SimpleJson.text(entry.getValue())
        ));
    }

}
