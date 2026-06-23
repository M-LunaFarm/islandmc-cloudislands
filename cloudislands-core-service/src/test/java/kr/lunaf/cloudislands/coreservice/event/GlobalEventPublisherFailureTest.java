package kr.lunaf.cloudislands.coreservice.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.job.InMemoryJobCompletionOutboxStore;
import kr.lunaf.cloudislands.coreservice.job.JobCompletionOutboxDispatcher;
import kr.lunaf.cloudislands.coreservice.job.RedisStreamJobPublisher.RedisStreamWriter;
import org.junit.jupiter.api.Test;

class GlobalEventPublisherFailureTest {
    @Test
    void compositePublisherKeepsAuthoritativeWritePathAliveWhenOneEventSinkFails() {
        InMemoryGlobalEventPublisher inMemory = new InMemoryGlobalEventPublisher();
        GlobalEventPublisher failingSink = (_eventType, _fields) -> {
            throw new IllegalStateException("redis stream unavailable");
        };
        CompositeGlobalEventPublisher composite = new CompositeGlobalEventPublisher(List.of(failingSink, inMemory));

        assertDoesNotThrow(() -> composite.publish(
            CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(),
            Map.of("islandId", "00000000-0000-0000-0000-000000000001")
        ));

        assertEquals(1L, inMemory.countByType(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name()));
        assertTrue(inMemory.toJson().contains(RedisKeys.islandMembers(java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"))));
    }

    @Test
    void redisStreamEventPublisherCountsFailuresInsteadOfThrowingIntoCoreMutations() {
        RedisStreamWriter failingWriter = (_stream, _fieldValues) -> {
            throw new IllegalStateException("redis stream unavailable");
        };
        RedisStreamEventPublisher publisher = new RedisStreamEventPublisher(failingWriter);

        assertDoesNotThrow(() -> publisher.publish(
            CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(),
            Map.of("islandId", "00000000-0000-0000-0000-000000000002")
        ));

        assertEquals(1L, publisher.failuresTotal());
    }

    @Test
    void redisStreamEventPublisherCanRethrowForDurableOutboxDispatch() {
        RedisStreamWriter failingWriter = (_stream, _fieldValues) -> {
            throw new IllegalStateException("redis stream unavailable");
        };
        RedisStreamEventPublisher publisher = new RedisStreamEventPublisher(failingWriter);

        assertThrows(IllegalStateException.class, () -> publisher.rethrowingPublisher().publish(
            CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(),
            Map.of("islandId", "00000000-0000-0000-0000-000000000002")
        ));

        assertEquals(1L, publisher.failuresTotal());
    }

    @Test
    void failFastPublisherStopsBeforeLocalObservationWhenDurableSinkFails() {
        InMemoryGlobalEventPublisher inMemory = new InMemoryGlobalEventPublisher();
        GlobalEventPublisher failingSink = (_eventType, _fields) -> {
            throw new IllegalStateException("redis stream unavailable");
        };
        FailFastGlobalEventPublisher publisher = new FailFastGlobalEventPublisher(List.of(failingSink, inMemory));

        assertThrows(IllegalStateException.class, () -> publisher.publish(
            CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(),
            Map.of("islandId", "00000000-0000-0000-0000-000000000003")
        ));

        assertEquals(0L, inMemory.countByType(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name()));
    }

    @Test
    void failFastPublisherRecordsLocalObservationAfterDurableSinkSucceeds() {
        InMemoryGlobalEventPublisher inMemory = new InMemoryGlobalEventPublisher();
        AtomicInteger durableCalls = new AtomicInteger();
        GlobalEventPublisher durableSink = (_eventType, _fields) -> durableCalls.incrementAndGet();
        FailFastGlobalEventPublisher publisher = new FailFastGlobalEventPublisher(List.of(durableSink, inMemory));

        publisher.publish(
            CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name(),
            Map.of("islandId", "00000000-0000-0000-0000-000000000004")
        );

        assertEquals(1, durableCalls.get());
        assertEquals(1L, inMemory.countByType(CloudIslandEventType.ISLAND_SNAPSHOT_CREATED.name()));
    }

    @Test
    void outboxPublisherPersistsNormalEventsBeforeDispatch() {
        InMemoryJobCompletionOutboxStore outbox = new InMemoryJobCompletionOutboxStore();
        InMemoryGlobalEventPublisher inMemory = new InMemoryGlobalEventPublisher();
        OutboxGlobalEventPublisher publisher = new OutboxGlobalEventPublisher(outbox);
        JobCompletionOutboxDispatcher dispatcher = new JobCompletionOutboxDispatcher(outbox, inMemory);

        publisher.publish(
            CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(),
            Map.of("islandId", "00000000-0000-0000-0000-000000000005")
        );

        assertEquals(1L, outbox.pendingCount());
        assertEquals(0L, inMemory.countByType(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name()));

        dispatcher.dispatchDue();

        assertEquals(0L, outbox.pendingCount());
        assertEquals(1L, inMemory.countByType(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name()));
        assertTrue(inMemory.toJson().contains("\"aggregateId\":\"00000000-0000-0000-0000-000000000005\""));
    }
}
