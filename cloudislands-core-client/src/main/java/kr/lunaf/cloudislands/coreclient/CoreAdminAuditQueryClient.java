package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreAdminAuditQueryClient implements AdminAuditQueryClient {
    private final CoreApiClient delegate;

    public CoreAdminAuditQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<AdminAuditEntryView>> list(int limit) {
        return delegate.listAuditLogs(Math.max(1, Math.min(limit, 500))).thenApply(CoreAdminAuditQueryClient::entries);
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
