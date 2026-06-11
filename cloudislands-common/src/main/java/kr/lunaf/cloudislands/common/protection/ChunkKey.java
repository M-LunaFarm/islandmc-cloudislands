package kr.lunaf.cloudislands.common.protection;

public record ChunkKey(String world, int chunkX, int chunkZ) {
    public static ChunkKey fromBlock(String world, int blockX, int blockZ) {
        return new ChunkKey(world, Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
    }
}
