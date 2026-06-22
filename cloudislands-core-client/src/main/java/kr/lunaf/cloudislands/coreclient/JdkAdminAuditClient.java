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
        return core.postWithResultBody("/v1/admin/audit/list", CoreJsonPayload.object("limit", Math.max(1, Math.min(limit, 500))))
            .thenApply(JdkAdminAuditClient::entries);
    }

    static List<AdminAuditEntryView> entries(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return SimpleJson.list(root.get("audit")).stream()
            .map(SimpleJson::object)
            .map(entry -> new AdminAuditEntryView(
                text(entry, "id"),
                text(entry, "actorUuid"),
                text(entry, "actorType"),
                text(entry, "action"),
                text(entry, "targetType"),
                text(entry, "targetId"),
                stringMap(SimpleJson.object(entry.get("payload"))),
                text(entry, "createdAt")
            ))
            .toList();
    }

    private static Map<String, String> stringMap(Map<?, ?> object) {
        return object.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
            entry -> SimpleJson.text(entry.getKey()),
            entry -> SimpleJson.text(entry.getValue())
        ));
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }
}
