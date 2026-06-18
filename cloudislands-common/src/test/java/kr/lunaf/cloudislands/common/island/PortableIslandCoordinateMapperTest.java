package kr.lunaf.cloudislands.common.island;

import org.junit.jupiter.api.Test;

import static kr.lunaf.cloudislands.common.island.PortableIslandCoordinateMapper.LogicalPoint;
import static kr.lunaf.cloudislands.common.island.PortableIslandCoordinateMapper.PhysicalPoint;
import static kr.lunaf.cloudislands.common.island.PortableIslandCoordinateMapper.RuntimePlacement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PortableIslandCoordinateMapperTest {
    @Test
    void mapsLogicalIslandCoordinatesIntoCurrentRuntimeCell() {
        RuntimePlacement placement = new RuntimePlacement("island-1", "ci_shard_001", 2, 0, 1024);
        LogicalPoint logical = new LogicalPoint(0.5, 100.0, 0.5, 180.0f, 0.0f);

        PhysicalPoint physical = PortableIslandCoordinateMapper.toPhysical(logical, placement);

        assertEquals("ci_shard_001", physical.worldName());
        assertEquals(2048.5, physical.blockX());
        assertEquals(100.0, physical.blockY());
        assertEquals(0.5, physical.blockZ());
        assertEquals(180.0f, physical.yaw());
        assertEquals(0.0f, physical.pitch());
    }

    @Test
    void remapsSameLogicalPointToDifferentNodeAndCell() {
        RuntimePlacement source = new RuntimePlacement("island-1", "ci_shard_001", 2, 0, 1024);
        RuntimePlacement target = new RuntimePlacement("island-2", "ci_shard_003", 5, 3, 1024);
        PhysicalPoint sourcePhysical = new PhysicalPoint("ci_shard_001", 2058.5, 100.0, 20.5, 90.0f, 10.0f);

        LogicalPoint logical = PortableIslandCoordinateMapper.toLogical(sourcePhysical, source);
        PhysicalPoint targetPhysical = PortableIslandCoordinateMapper.remap(logical, target);

        assertEquals(new LogicalPoint(10.5, 100.0, 20.5, 90.0f, 10.0f), logical);
        assertEquals("ci_shard_003", targetPhysical.worldName());
        assertEquals(5130.5, targetPhysical.blockX());
        assertEquals(3092.5, targetPhysical.blockZ());
        assertEquals(100.0, targetPhysical.blockY());
    }

    @Test
    void rejectsInvalidRuntimePlacement() {
        assertThrows(IllegalArgumentException.class, () -> new RuntimePlacement("", "ci_shard_001", 0, 0, 1024));
        assertThrows(IllegalArgumentException.class, () -> new RuntimePlacement("island-1", "", 0, 0, 1024));
        assertThrows(IllegalArgumentException.class, () -> new RuntimePlacement("island-1", "ci_shard_001", 0, 0, 0));
    }
}
