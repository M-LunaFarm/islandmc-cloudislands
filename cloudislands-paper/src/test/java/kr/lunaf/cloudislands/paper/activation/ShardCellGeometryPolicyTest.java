package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ShardCellGeometryPolicyTest {
    @Test
    void cellSizesMustKeepRegionFilesAlignedAndSeparated() {
        assertDoesNotThrow(() -> ShardCellGeometryPolicy.requireSafeCellSize(1024));
        assertDoesNotThrow(() -> ShardCellGeometryPolicy.requireSafeCellSize(2048));
        assertThrows(ShardCellGeometryPolicy.UnsafeGeometryException.class, () -> ShardCellGeometryPolicy.requireSafeCellSize(512));
        assertThrows(ShardCellGeometryPolicy.UnsafeGeometryException.class, () -> ShardCellGeometryPolicy.requireSafeCellSize(1000));
    }

    @Test
    void islandBoundsMustNotReachTheAdjacentCellRegionFiles() {
        assertTrue(ShardCellGeometryPolicy.supportsIslandSize(1024, 1023));
        assertFalse(ShardCellGeometryPolicy.supportsIslandSize(1024, 1024));
        assertFalse(ShardCellGeometryPolicy.supportsIslandSize(1024, 2048));
        assertFalse(ShardCellGeometryPolicy.supportsIslandSize(1024, 0));
    }
}
