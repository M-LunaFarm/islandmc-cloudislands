package kr.lunaf.cloudislands.coreservice.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RedisListCachePayloadTest {
    @Test
    void acceptsOnlyCompleteNonEmptyLinePayloads() {
        assertEquals(List.of("one", "two"), RedisListCachePayload.complete("row-1\nrow-2\n", List.of("one", "two")).orElseThrow());
        assertTrue(RedisListCachePayload.complete("row-1\ncorrupt-row\n", List.of("one")).isEmpty());
        assertTrue(RedisListCachePayload.complete("corrupt-row", List.of()).isEmpty());
        assertTrue(RedisListCachePayload.complete("", List.of()).isEmpty());
        assertEquals(List.of(), RedisListCachePayload.complete(RedisListCachePayload.emptyPayload(), List.of()).orElseThrow());
        assertTrue(RedisListCachePayload.complete(RedisListCachePayload.emptyPayload() + "\ncorrupt-row", List.of()).isEmpty());
    }
}
