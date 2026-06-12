package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.GlobalEventBatchSnapshot;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;

public interface IslandEventService {
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents();
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents(int limit);
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEventsSince(long sinceSeq, int limit);

    default CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch() {
        return listGlobalEvents().thenApply(IslandEventService::batch);
    }

    default CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch(int limit) {
        return listGlobalEvents(limit).thenApply(IslandEventService::batch);
    }

    default CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatchSince(long sinceSeq, int limit) {
        return listGlobalEventsSince(sinceSeq, limit).thenApply(IslandEventService::batch);
    }

    private static GlobalEventBatchSnapshot batch(List<GlobalEventSnapshot> events) {
        long oldestSequence = events.stream().mapToLong(GlobalEventSnapshot::sequence).filter(sequence -> sequence > 0L).min().orElse(0L);
        long latestSequence = events.stream().mapToLong(GlobalEventSnapshot::sequence).max().orElse(0L);
        return new GlobalEventBatchSnapshot(oldestSequence, latestSequence, events);
    }
}
