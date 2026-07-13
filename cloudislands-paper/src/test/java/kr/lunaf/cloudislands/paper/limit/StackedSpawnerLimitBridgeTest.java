package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class StackedSpawnerLimitBridgeTest {
    @Test
    void roseExistingStackIncreaseNeedsTheFullLogicalDelta() {
        assertEquals(12L, StackedSpawnerLimitBridge.roseSupplementalDelta(false, 20L, 12L));
        assertEquals(12L, StackedSpawnerLimitBridge.roseSupplementalDelta(true, 1L, 12L));
    }

    @Test
    void roseNewPhysicalBlockSubtractsTheBukkitPlacementDelta() {
        assertEquals(11L, StackedSpawnerLimitBridge.roseSupplementalDelta(true, 0L, 12L));
        assertEquals(0L, StackedSpawnerLimitBridge.roseSupplementalDelta(true, 0L, 1L));
    }

    @Test
    void wildNewPhysicalBlockSubtractsTheBukkitPlacementDelta() {
        assertEquals(11L, StackedSpawnerLimitBridge.wildPlacementSupplementalDelta(12L));
        assertEquals(0L, StackedSpawnerLimitBridge.wildPlacementSupplementalDelta(1L));
    }
}
