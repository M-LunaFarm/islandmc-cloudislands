package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SafeTeleportResolverTest {
    @Test
    void buildHeightGuardLeavesARealFloorAndHeadBlockInsideTheWorld() {
        assertFalse(SafeTeleportResolver.withinBuildHeight(-63, -64, 320));
        assertTrue(SafeTeleportResolver.withinBuildHeight(-62, -64, 320));
        assertTrue(SafeTeleportResolver.withinBuildHeight(318, -64, 320));
        assertFalse(SafeTeleportResolver.withinBuildHeight(319, -64, 320));
    }
}
