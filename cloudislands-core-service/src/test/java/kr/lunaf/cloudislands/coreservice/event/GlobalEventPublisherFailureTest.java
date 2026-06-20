package kr.lunaf.cloudislands.coreservice.event;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import kr.lunaf.cloudislands.common.cache.RedisKeys;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
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
}
