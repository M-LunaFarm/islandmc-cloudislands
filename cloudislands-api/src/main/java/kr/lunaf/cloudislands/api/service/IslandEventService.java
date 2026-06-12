package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.GlobalEventBatchSnapshot;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;

public interface IslandEventService {
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents();
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents(int limit);
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEventsSince(long sinceSeq, int limit);
    CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch();
    CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatch(int limit);
    CompletableFuture<GlobalEventBatchSnapshot> listGlobalEventBatchSince(long sinceSeq, int limit);
}
