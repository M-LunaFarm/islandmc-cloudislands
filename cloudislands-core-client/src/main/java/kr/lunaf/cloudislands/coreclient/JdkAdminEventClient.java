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
        return core.postResultBody("/v1/events", CoreJsonPayload.object("limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminEventClient::stream);
    }

    @Override
    public CompletableFuture<AdminEventStreamView> listSince(long sinceSeq, int limit) {
        return core.postResultBody("/v1/events", CoreJsonPayload.object("sinceSeq", Math.max(0L, sinceSeq), "limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkAdminEventClient::stream);
    }

    static AdminEventStreamView stream(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<AdminEventView> events = CoreJson.objects(root, "events").stream()
            .map(event -> new AdminEventView(
                CoreJson.number(event, "seq"),
                CoreJson.text(event, "type"),
                stringMap(SimpleJson.object(event.get("fields"))),
                CoreJson.text(event, "occurredAt")
            ))
            .toList();
        return new AdminEventStreamView(CoreJson.number(root, "oldestSeq"), CoreJson.number(root, "latestSeq"), events);
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

}
