package kr.lunaf.cloudislands.paper.environment;

import java.util.ArrayList;
import java.util.List;
import kr.lunaf.cloudislands.common.protection.IslandRegion;

public final class IslandBiomePaintPlan {
    public static final int SAMPLE_STEP = 4;

    private IslandBiomePaintPlan() {
    }

    public static List<ChunkCoordinate> chunkCoordinates(IslandRegion region) {
        int minChunkX = Math.floorDiv(region.minX(), 16);
        int maxChunkX = Math.floorDiv(region.maxX(), 16);
        int minChunkZ = Math.floorDiv(region.minZ(), 16);
        int maxChunkZ = Math.floorDiv(region.maxZ(), 16);
        List<ChunkCoordinate> chunks = new ArrayList<>((maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1));
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                chunks.add(new ChunkCoordinate(chunkX, chunkZ));
            }
        }
        return List.copyOf(chunks);
    }

    public static int alignedStart(int coordinate) {
        return Math.floorDiv(coordinate + SAMPLE_STEP - 1, SAMPLE_STEP) * SAMPLE_STEP;
    }

    public record ChunkCoordinate(int x, int z) {}
}
