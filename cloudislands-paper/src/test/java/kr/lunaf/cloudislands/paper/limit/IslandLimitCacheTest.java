package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
import org.junit.jupiter.api.Test;

class IslandLimitCacheTest {
    @Test
    void failsClosedUntilLimitsAndAuthoritativeCountsAreReady() {
        UUID islandId = UUID.randomUUID();
        CompletableFuture<List<CoreGuiViews.LimitView>> limits = new CompletableFuture<>();
        CompletableFuture<Map<String, Long>> counts = new CompletableFuture<>();
        IslandLimitCache cache = new IslandLimitCache(ignored -> limits, ignored -> counts);

        assertTrue(cache.limitIfReady(islandId, "SPAWNER", Long.MAX_VALUE).isEmpty());
        limits.complete(List.of(new CoreGuiViews.LimitView("SPAWNER", 20L, "")));
        assertEquals(20L, cache.limitIfReady(islandId, "SPAWNER", Long.MAX_VALUE).orElseThrow());

        assertTrue(cache.blockCountIfReady(islandId, "SPAWNER").isEmpty());
        counts.complete(Map.of("minecraft:spawner", 12L));
        assertEquals(12L, cache.blockCountIfReady(islandId, "SPAWNER").orElseThrow());
    }

    @Test
    void aggregatesLimitCategoriesAndTracksAcceptedDeltas() {
        UUID islandId = UUID.randomUUID();
        IslandLimitCache cache = new IslandLimitCache(
            ignored -> CompletableFuture.completedFuture(List.of()),
            ignored -> CompletableFuture.completedFuture(Map.of(
                "minecraft:hopper", 4L,
                "minecraft:spawner", 9L,
                "cloudislands:limit/spawner", 14L,
                "cloudislands:limit/entity", 22L,
                "entity:minecraft:zombie", 8L,
                "minecraft:redstone_wire", 3L,
                "minecraft:oak_button", 2L,
                "minecraft:stone", 100L
            ))
        );

        assertEquals(OptionalLong.empty(), cache.blockCountIfReady(islandId, "REDSTONE"));
        assertEquals(5L, cache.blockCountIfReady(islandId, "REDSTONE").orElseThrow());
        assertEquals(4L, cache.blockCountIfReady(islandId, "HOPPER").orElseThrow());
        assertEquals(14L, cache.blockCountIfReady(islandId, "SPAWNER").orElseThrow());
        assertEquals(22L, cache.blockCountIfReady(islandId, "ENTITY").orElseThrow());
        assertEquals(8L, cache.blockCountIfReady(islandId, GameplayParityPolicy.entityTypeLimitKey("zombie")).orElseThrow());
        assertEquals(100L, cache.blockCountIfReady(islandId, GameplayParityPolicy.blockAmountLimitKey("stone")).orElseThrow());

        cache.recordBlockDelta(islandId, "minecraft:spawner", 3L);
        cache.recordBlockDelta(islandId, "cloudislands:limit/spawner", 3L);
        cache.recordBlockDelta(islandId, "minecraft:oak_button", -1L);

        assertEquals(17L, cache.blockCountIfReady(islandId, "SPAWNER").orElseThrow());
        assertEquals(4L, cache.blockCountIfReady(islandId, "REDSTONE").orElseThrow());
    }

    @Test
    void replacementAndLifecycleInvalidationDiscardStaleSnapshots() {
        UUID islandId = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        IslandLimitCache cache = new IslandLimitCache(
            ignored -> CompletableFuture.completedFuture(List.of()),
            ignored -> CompletableFuture.completedFuture(Map.of("minecraft:hopper", (long) loads.incrementAndGet()))
        );

        cache.blockCountIfReady(islandId, "HOPPER");
        assertEquals(1L, cache.blockCountIfReady(islandId, "HOPPER").orElseThrow());
        cache.replaceBlockCounts(islandId, Map.of("minecraft:hopper", 7L));
        assertEquals(7L, cache.blockCountIfReady(islandId, "HOPPER").orElseThrow());

        cache.invalidate(islandId);
        assertTrue(cache.blockCountIfReady(islandId, "HOPPER").isEmpty());
        assertEquals(2L, cache.blockCountIfReady(islandId, "HOPPER").orElseThrow());
    }

    @Test
    void firstInternalDeltaMigratesLegacyMaterialCountWithoutResettingIt() {
        UUID islandId = UUID.randomUUID();
        IslandLimitCache cache = new IslandLimitCache(
            ignored -> CompletableFuture.completedFuture(List.of()),
            ignored -> CompletableFuture.completedFuture(Map.of("minecraft:spawner", 9L))
        );

        cache.blockCountIfReady(islandId, "SPAWNER");
        assertEquals(9L, cache.blockCountIfReady(islandId, "SPAWNER").orElseThrow());
        cache.recordBlockDelta(islandId, "cloudislands:limit/spawner", 3L);

        assertEquals(12L, cache.blockCountIfReady(islandId, "SPAWNER").orElseThrow());
    }

    @Test
    void firstEntityLimitDeltaMigratesLegacyEntityCounts() {
        UUID islandId = UUID.randomUUID();
        IslandLimitCache cache = new IslandLimitCache(
            ignored -> CompletableFuture.completedFuture(List.of()),
            ignored -> CompletableFuture.completedFuture(Map.of(
                "entity:minecraft:zombie", 7L,
                "entity:minecraft:cow", 3L,
                "minecraft:stone", 100L
            ))
        );

        cache.blockCountIfReady(islandId, "ENTITY");
        assertEquals(10L, cache.blockCountIfReady(islandId, "ENTITY").orElseThrow());
        cache.recordBlockDelta(islandId, IslandEntityLimitKeys.COUNT_KEY, 1L);

        assertEquals(11L, cache.blockCountIfReady(islandId, "ENTITY").orElseThrow());
    }
}
