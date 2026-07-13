package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogicalEntitySpawnBridgeTest {
    @Test
    void countsOnlySpawnsAddedDirectlyToExistingLogicalStacks() {
        assertEquals(9L, LogicalEntitySpawnBridge.directLogicalDelta(12L, 3L));
        assertEquals(0L, LogicalEntitySpawnBridge.directLogicalDelta(3L, 3L));
        assertEquals(0L, LogicalEntitySpawnBridge.directLogicalDelta(2L, 4L));
    }
}
