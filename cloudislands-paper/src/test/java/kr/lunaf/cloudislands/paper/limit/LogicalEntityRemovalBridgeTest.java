package kr.lunaf.cloudislands.paper.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LogicalEntityRemovalBridgeTest {
    @Test
    void subtractsThePhysicalDeathAlreadyHandledByBukkitListeners() {
        assertEquals(0L, LogicalEntityRemovalBridge.supplementalRemoval(0L));
        assertEquals(0L, LogicalEntityRemovalBridge.supplementalRemoval(1L));
        assertEquals(4L, LogicalEntityRemovalBridge.supplementalRemoval(5L));
        assertEquals(63L, LogicalEntityRemovalBridge.supplementalRemoval(64L));
    }
}
