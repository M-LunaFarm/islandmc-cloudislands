package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreAdminEventQueryClient implements AdminEventQueryClient {
    private final CoreApiClient delegate;

    public CoreAdminEventQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<AdminEventStreamView> list(int limit) {
        return delegate.listEvents(Math.max(1, Math.min(limit, 4096))).thenApply(CoreAdminEventQueryClient::stream);
    }

    @Override
    public CompletableFuture<AdminEventStreamView> listSince(long sinceSeq, int limit) {
        return delegate.listEventsSince(Math.max(0L, sinceSeq), Math.max(1, Math.min(limit, 4096))).thenApply(CoreAdminEventQueryClient::stream);
    }

    static AdminEventStreamView stream(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminEventView> events = SimpleJson.list(root.get("events")).stream()
            .map(SimpleJson::object)
            .map(event -> new AdminEventView(
                SimpleJson.number(event.get("seq")),
                text(event, "type"),
                stringMap(SimpleJson.object(event.get("fields"))),
                text(event, "occurredAt")
            ))
            .toList();
        return new AdminEventStreamView(SimpleJson.number(root.get("oldestSeq")), SimpleJson.number(root.get("latestSeq")), events);
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
