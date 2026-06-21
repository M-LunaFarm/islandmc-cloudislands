package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreCommunicationQueryClient implements CommunicationQueryClient {
    private final CoreApiClient delegate;

    public CoreCommunicationQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.LogEntryView>> listLogs(UUID islandId, int limit) {
        requireIsland(islandId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return CoreGuiViews.islandLogs(delegate, islandId, safeLimit);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
