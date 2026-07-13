package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogicalStackDeltaBridgeTest {
    @Test
    void roseExistingStackIncreaseNeedsTheFullLogicalDelta() {
        assertEquals(12L, LogicalStackDeltaBridge.roseIncreaseDelta(false, 20L, 12L));
        assertEquals(12L, LogicalStackDeltaBridge.roseIncreaseDelta(true, 1L, 12L));
    }

    @Test
    void roseNewPhysicalBlockSubtractsTheBukkitPlacementDelta() {
        assertEquals(11L, LogicalStackDeltaBridge.roseIncreaseDelta(true, 0L, 12L));
        assertEquals(0L, LogicalStackDeltaBridge.roseIncreaseDelta(true, 0L, 1L));
    }

    @Test
    void wildNewSpawnerSubtractsTheAcceptedPhysicalPlacement() {
        assertEquals(11L, LogicalStackDeltaBridge.wildSpawnerPlacementDelta(12L));
        assertEquals(0L, LogicalStackDeltaBridge.wildSpawnerPlacementDelta(1L));
    }

    @Test
    void wildSpawnerExplosionSubtractsOnlyTheFinalPhysicalBreak() {
        assertEquals(11L, LogicalStackDeltaBridge.wildSpawnerDecreaseDelta(12L, 12L, false));
        assertEquals(3L, LogicalStackDeltaBridge.wildSpawnerDecreaseDelta(12L, 3L, false));
        assertEquals(12L, LogicalStackDeltaBridge.wildSpawnerDecreaseDelta(12L, 12L, true));
        assertEquals(11L, LogicalStackDeltaBridge.wildSpawnerDecreaseDelta(12L, 99L, false));
    }
}
