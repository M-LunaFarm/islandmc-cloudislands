package kr.lunaf.cloudislands.paper.environment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.common.protection.IslandRegion;
import org.junit.jupiter.api.Test;

class IslandBiomeRuntimeApplierTest {
    @Test
    void plansEveryIntersectingChunkAcrossNegativeCoordinates() {
        IslandRegion region = new IslandRegion(UUID.randomUUID(), "islands", -17, 17, -1, 16, 0, 0);

        List<IslandBiomePaintPlan.ChunkCoordinate> chunks = IslandBiomePaintPlan.chunkCoordinates(region);

        assertEquals(12, chunks.size());
        assertEquals(new IslandBiomePaintPlan.ChunkCoordinate(-2, -1), chunks.getFirst());
        assertEquals(new IslandBiomePaintPlan.ChunkCoordinate(1, 1), chunks.getLast());
    }

    @Test
    void alignsBiomeSamplesToGlobalQuartCoordinates() {
        assertEquals(-4, IslandBiomePaintPlan.alignedStart(-5));
        assertEquals(-4, IslandBiomePaintPlan.alignedStart(-4));
        assertEquals(0, IslandBiomePaintPlan.alignedStart(-1));
        assertEquals(4, IslandBiomePaintPlan.alignedStart(1));
    }
}
