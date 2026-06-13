package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import kr.lunaf.cloudislands.api.event.CloudEvent;
import kr.lunaf.cloudislands.api.event.CloudEventMapper;
import kr.lunaf.cloudislands.api.model.GlobalEventBatchSnapshot;
import kr.lunaf.cloudislands.api.model.GlobalEventSnapshot;

public interface IslandEventService {
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents();
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEvents(int limit);
    CompletableFuture<List<GlobalEventSnapshot>> listGlobalEventsSince(long sinceSeq, int limit);
    default GlobalEventSubscription subscribeGlobalEvents(long sinceSeq, int limit, long intervalTicks, Consumer<List<GlobalEventSnapshot>> listener) {
        Objects.requireNonNull(listener, "listener");
        AtomicLong cursor = new AtomicLong(Math.max(0L, sinceSeq));
        int safeLimit = Math.max(1, limit);
        long safeIntervalMillis = Math.max(50L, Math.max(1L, intervalTicks) * 50L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "cloudislands-api-events");
            thread.setDaemon(true);
            return thread;
        });
        ScheduledFuture<?> future = executor.scheduleWithFixedDelay(() ->
            listGlobalEventsSince(cursor.get(), safeLimit)
                .thenAccept(events -> {
                    long previousSequence = cursor.get();
                    List<GlobalEventSnapshot> freshEvents = events.stream()
                        .filter(event -> event.sequence() > previousSequence)
                        .toList();
                    if (freshEvents.isEmpty()) {
                        return;
                    }
                    long latestSequence = freshEvents.stream().mapToLong(GlobalEventSnapshot::sequence).max().orElse(previousSequence);
                    cursor.set(Math.max(cursor.get(), latestSequence));
                    listener.accept(List.copyOf(freshEvents));
                })
                .exceptionally(_error -> null), 0L, safeIntervalMillis, TimeUnit.MILLISECONDS);
        return () -> {
            future.cancel(false);
            executor.shutdownNow();
        };
    }

    default GlobalEventSubscription subscribeGlobalEvents(Consumer<List<GlobalEventSnapshot>> listener) {
        return subscribeGlobalEvents(0L, 64, 20L, listener);
    }

    default CompletableFuture<List<CloudEvent>> listTypedGlobalEvents() {
        return listGlobalEvents().thenApply(IslandEventService::typed);
    }

    default CompletableFuture<List<CloudEvent>> listTypedGlobalEvents(int limit) {
        return listGlobalEvents(limit).thenApply(IslandEventService::typed);
    }

    default CompletableFuture<List<CloudEvent>> listTypedGlobalEventsSince(long sinceSeq, int limit) {
        return listGlobalEventsSince(sinceSeq, limit).thenApply(IslandEventService::typed);
    }

    default GlobalEventSubscription subscribeTypedGlobalEvents(long sinceSeq, int limit, long intervalTicks, Consumer<List<CloudEvent>> listener) {
        return subscribeGlobalEvents(sinceSeq, limit, intervalTicks, events -> {
            List<CloudEvent> typedEvents = typed(events);
            if (!typedEvents.isEmpty()) {
                listener.accept(typedEvents);
            }
        });
    }

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

    private static List<CloudEvent> typed(List<GlobalEventSnapshot> events) {
        return events.stream().map(CloudEventMapper::map).flatMap(java.util.Optional::stream).toList();
    }

    interface GlobalEventSubscription extends AutoCloseable {
        @Override
        void close();
    }
}
