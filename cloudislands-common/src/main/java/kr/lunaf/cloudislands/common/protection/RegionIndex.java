package kr.lunaf.cloudislands.common.protection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RegionIndex {
    private final Map<ChunkKey, List<IslandRegion>> regionsByChunk = new ConcurrentHashMap<>();

    public void add(IslandRegion region) {
        int minChunkX = Math.floorDiv(region.minX(), 16);
        int maxChunkX = Math.floorDiv(region.maxX(), 16);
        int minChunkZ = Math.floorDiv(region.minZ(), 16);
        int maxChunkZ = Math.floorDiv(region.maxZ(), 16);
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                regionsByChunk.computeIfAbsent(new ChunkKey(region.world(), chunkX, chunkZ), ignored -> new ArrayList<>()).add(region);
            }
        }
    }

    public Optional<IslandRegion> find(String world, int blockX, int blockZ) {
        return regionsByChunk.getOrDefault(ChunkKey.fromBlock(world, blockX, blockZ), List.of()).stream()
            .filter(region -> region.contains(world, blockX, blockZ))
            .findFirst();
    }
}
