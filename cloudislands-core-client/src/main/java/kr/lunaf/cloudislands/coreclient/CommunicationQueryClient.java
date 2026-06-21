package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLogRecord;

public interface CommunicationQueryClient {
    CompletableFuture<List<IslandLogRecord>> records(UUID islandId, int limit);

    default CompletableFuture<List<CoreGuiViews.LogEntryView>> listLogs(UUID islandId, int limit) {
        return records(islandId, limit).thenApply(logs -> logs.stream()
            .map(CoreCommunicationJson::view)
            .toList());
    }
}
