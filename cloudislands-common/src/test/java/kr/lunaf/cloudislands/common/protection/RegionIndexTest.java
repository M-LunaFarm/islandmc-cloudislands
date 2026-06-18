package kr.lunaf.cloudislands.common.protection;

import kr.lunaf.cloudislands.common.island.PortableIslandCoordinateMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void buildsProtectionRegionFromCurrentRuntimePlacement() {
        PortableIslandCoordinateMapper.RuntimePlacement source =
                new PortableIslandCoordinateMapper.RuntimePlacement("island-1", "ci_shard_001", 2, 0, 1024);
        PortableIslandCoordinateMapper.RuntimePlacement target =
                new PortableIslandCoordinateMapper.RuntimePlacement("island-2", "ci_shard_003", 5, 3, 1024);

        IslandRegion sourceRegion = IslandRegion.fromRuntimePlacement(ISLAND, source, 150);
        IslandRegion targetRegion = IslandRegion.fromRuntimePlacement(ISLAND, target, 150);

        assertEquals("ci_shard_001", sourceRegion.world());
        assertEquals(1898, sourceRegion.minX());
        assertEquals(2198, sourceRegion.maxX());
        assertEquals(-150, sourceRegion.minZ());
        assertEquals(150, sourceRegion.maxZ());
        assertEquals("ci_shard_003", targetRegion.world());
        assertEquals(4970, targetRegion.minX());
        assertEquals(5270, targetRegion.maxX());
        assertEquals(2922, targetRegion.minZ());
        assertEquals(3222, targetRegion.maxZ());
        assertTrue(targetRegion.contains("ci_shard_003", 5120, 3072));
        assertTrue(targetRegion.contains("ci_shard_001", 5120, 3072) == false);
    }

    @Test
    void rejectsNegativeLogicalIslandRegionSize() {
        PortableIslandCoordinateMapper.RuntimePlacement placement =
                new PortableIslandCoordinateMapper.RuntimePlacement("island-1", "ci_shard_001", 0, 0, 1024);

        assertThrows(IllegalArgumentException.class, () -> IslandRegion.fromRuntimePlacement(ISLAND, placement, -1));
    }
}
