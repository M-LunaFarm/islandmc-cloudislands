package kr.lunaf.cloudislands.common.protection;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionIndexTest {
    private static final UUID ISLAND = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000502");

    @Test
    void findsRegionByWorldChunkAndBoundingBoxIncludingEdges() {
        RegionIndex index = new RegionIndex();
        index.add(new IslandRegion(ISLAND, "ci_shard_001", -32, 32, -32, 32, 2, 3));

        assertEquals(ISLAND, index.find("ci_shard_001", 0, 0).orElseThrow().islandId());
        assertEquals(ISLAND, index.find("ci_shard_001", -32, -32).orElseThrow().islandId());
        assertEquals(ISLAND, index.find("ci_shard_001", 32, 32).orElseThrow().islandId());
        assertTrue(index.find("ci_shard_001", 33, 0).isEmpty());
        assertTrue(index.find("ci_shard_002", 0, 0).isEmpty());
    }

    @Test
    void keepsDifferentIslandsSeparatedInsideSharedChunks() {
        RegionIndex index = new RegionIndex();
        index.add(new IslandRegion(ISLAND, "ci_shard_001", 0, 7, 0, 7, 0, 0));
        index.add(new IslandRegion(OTHER, "ci_shard_001", 8, 15, 8, 15, 1, 0));

        assertEquals(ISLAND, index.find("ci_shard_001", 3, 3).orElseThrow().islandId());
        assertEquals(OTHER, index.find("ci_shard_001", 12, 12).orElseThrow().islandId());
        assertTrue(index.find("ci_shard_001", 7, 12).isEmpty());
        assertEquals(2, index.indexedIslandCount());
    }

    @Test
    void removingIslandClearsEveryIndexedChunkReference() {
        RegionIndex index = new RegionIndex();
        index.add(new IslandRegion(ISLAND, "ci_shard_001", -64, 64, -64, 64, 2, 3));

        assertTrue(index.indexedChunkCount() > 1);
        index.removeIsland(ISLAND);

        assertTrue(index.find("ci_shard_001", 0, 0).isEmpty());
        assertTrue(index.findIsland(ISLAND).isEmpty());
        assertEquals(0, index.indexedChunkCount());
        assertEquals(0, index.indexedIslandCount());
    }
}
