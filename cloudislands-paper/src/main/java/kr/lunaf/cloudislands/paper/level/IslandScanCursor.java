package kr.lunaf.cloudislands.paper.level;

final class IslandScanCursor {
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;
    private int x;
    private int y;
    private int z;
    private boolean exhausted;

    IslandScanCursor(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.x = minX;
        this.y = minY;
        this.z = minZ;
        this.exhausted = minX > maxX || minY > maxY || minZ > maxZ;
    }

    boolean hasNext() {
        return !exhausted;
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    int z() {
        return z;
    }

    boolean contains(int blockX, int blockZ) {
        return blockX >= minX && blockX <= maxX && blockZ >= minZ && blockZ <= maxZ;
    }

    void advance() {
        if (exhausted) {
            return;
        }
        if (y < maxY) {
            y++;
            return;
        }
        y = minY;
        if (z < maxZ) {
            z++;
            return;
        }
        z = minZ;
        if (x < maxX) {
            x++;
            return;
        }
        exhausted = true;
    }
}
