package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkAdminEventClient implements AdminEventQueryClient {
    private final JdkCoreApiClient core;

    JdkAdminEventClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<AdminEventStreamView> list(int limit) {
        return core.postWithResultBody("/v1/events", CoreJsonPayload.object("limit", boundedLimit(limit)))
            .thenApply(JdkAdminEventClient::stream);
    }

    @Override
    public CompletableFuture<AdminEventStreamView> listSince(long sinceSeq, int limit) {
        return core.postWithResultBody("/v1/events", CoreJsonPayload.object("sinceSeq", Math.max(0L, sinceSeq), "limit", boundedLimit(limit)))
            .thenApply(JdkAdminEventClient::stream);
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

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 4096));
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
