package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;

final class JdkCommunicationQueryClient implements CommunicationQueryClient {
    private final JdkCoreApiClient core;

    JdkCommunicationQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<IslandLogRecord>> records(UUID islandId, int limit) {
        requireId(islandId, "islandId");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return core.postBody("/v1/islands/logs", CoreJsonPayload.object("islandId", islandId, "limit", safeLimit))
            .thenApply(CoreCommunicationJson::records);
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
