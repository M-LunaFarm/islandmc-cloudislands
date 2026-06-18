package kr.lunaf.cloudislands.common.island;

public final class PortableIslandCoordinateMapper {
    private PortableIslandCoordinateMapper() {
    }

    public static PhysicalPoint toPhysical(LogicalPoint logical, RuntimePlacement placement) {
        return new PhysicalPoint(
                placement.worldName(),
                placement.originBlockX() + logical.localX(),
                logical.localY(),
                placement.originBlockZ() + logical.localZ(),
                logical.yaw(),
                logical.pitch()
        );
    }

    public static LogicalPoint toLogical(PhysicalPoint physical, RuntimePlacement placement) {
        return new LogicalPoint(
                physical.blockX() - placement.originBlockX(),
                physical.blockY(),
                physical.blockZ() - placement.originBlockZ(),
                physical.yaw(),
                physical.pitch()
        );
    }

    public static PhysicalPoint remap(LogicalPoint logical, RuntimePlacement targetPlacement) {
        return toPhysical(logical, targetPlacement);
    }

    public record RuntimePlacement(
            String nodeId,
            String worldName,
            int cellX,
            int cellZ,
            int cellSize
    ) {
        public RuntimePlacement {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            if (worldName == null || worldName.isBlank()) {
                throw new IllegalArgumentException("worldName is required");
            }
            if (cellSize <= 0) {
                throw new IllegalArgumentException("cellSize must be positive");
            }
        }

        public int originBlockX() {
            return cellX * cellSize;
        }

        public int originBlockZ() {
            return cellZ * cellSize;
        }
    }

    public record LogicalPoint(
            double localX,
            double localY,
            double localZ,
            float yaw,
            float pitch
    ) {
    }

    public record PhysicalPoint(
            String worldName,
            double blockX,
            double blockY,
            double blockZ,
            float yaw,
            float pitch
    ) {
    }
}
