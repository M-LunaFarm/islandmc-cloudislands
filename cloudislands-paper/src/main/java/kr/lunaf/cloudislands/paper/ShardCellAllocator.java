package kr.lunaf.cloudislands.paper;

public final class ShardCellAllocator {
    private final int cellSize;

    public ShardCellAllocator(int cellSize) {
        this.cellSize = cellSize;
    }

    public Cell cellForIndex(int index) {
        int x = Math.floorMod(index, 1024);
        int z = Math.floorDiv(index, 1024);
        return new Cell(x, z, x * cellSize, z * cellSize);
    }

    public Cell cellForCoordinates(int cellX, int cellZ) {
        int x = Math.max(0, cellX);
        int z = Math.max(0, cellZ);
        return new Cell(x, z, x * cellSize, z * cellSize);
    }

    public record Cell(int cellX, int cellZ, int originX, int originZ) {}
}
