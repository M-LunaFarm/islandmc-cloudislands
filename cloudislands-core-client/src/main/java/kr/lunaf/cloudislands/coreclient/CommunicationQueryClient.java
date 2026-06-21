package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface CommunicationQueryClient {
    CompletableFuture<List<CoreGuiViews.LogEntryView>> listLogs(UUID islandId, int limit);
}
