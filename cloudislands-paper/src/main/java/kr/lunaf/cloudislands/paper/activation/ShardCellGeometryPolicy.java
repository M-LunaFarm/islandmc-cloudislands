package kr.lunaf.cloudislands.paper.activation;

/** Protects portable region-file snapshots from overlapping adjacent shard cells. */
public final class ShardCellGeometryPolicy {
    public static final int REGION_FILE_BLOCK_SIZE = 512;
    public static final int MINIMUM_CELL_SIZE = REGION_FILE_BLOCK_SIZE * 2;

    private ShardCellGeometryPolicy() {
    }

    public static void requireSafeCellSize(int cellSize) {
        if (cellSize < MINIMUM_CELL_SIZE || cellSize % REGION_FILE_BLOCK_SIZE != 0) {
            throw new UnsafeGeometryException(
                "island-node.cell-size must be at least " + MINIMUM_CELL_SIZE
                    + " and aligned to " + REGION_FILE_BLOCK_SIZE + " blocks: " + cellSize
            );
        }
    }

    public static boolean supportsIslandSize(int cellSize, int islandSize) {
        if (islandSize <= 0) {
            return false;
        }
        long half = Math.max(1L, islandSize / 2L);
        long indexedSpan = half * 2L + 1L;
        return indexedSpan < cellSize;
    }

    public static void requireSupportedIslandSize(int cellSize, int islandSize) {
        if (!supportsIslandSize(cellSize, islandSize)) {
            throw new UnsafeGeometryException(
                "island size " + islandSize + " exceeds the non-overlapping capacity of cell-size " + cellSize
            );
        }
    }

    public static final class UnsafeGeometryException extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;

        public UnsafeGeometryException(String message) {
            super(message);
        }
    }
}
