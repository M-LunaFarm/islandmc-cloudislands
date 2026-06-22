package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
                CoreJson.stringMap(CoreJson.objectValue(event, "fields")),
                CoreJson.text(event, "occurredAt")
            ))
            .toList();
        return new AdminEventStreamView(CoreJson.number(root, "oldestSeq"), CoreJson.number(root, "latestSeq"), events);
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 4096));
    }

}
