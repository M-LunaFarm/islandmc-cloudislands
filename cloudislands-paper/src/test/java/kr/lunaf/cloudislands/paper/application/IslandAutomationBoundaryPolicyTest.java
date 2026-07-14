package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class IslandAutomationBoundaryPolicyTest {
    private static final UUID ISLAND_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ISLAND_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @Test
    void permitsAutomationOnlyWithinOneStableProtectionDomain() {
        assertFalse(IslandAutomationBoundaryPolicy.crossesBoundary(ISLAND_A, ISLAND_A, false, false));
        assertFalse(IslandAutomationBoundaryPolicy.crossesBoundary(null, null, false, false));

        assertTrue(IslandAutomationBoundaryPolicy.crossesBoundary(ISLAND_A, ISLAND_B, false, false));
        assertTrue(IslandAutomationBoundaryPolicy.crossesBoundary(ISLAND_A, null, false, false));
        assertTrue(IslandAutomationBoundaryPolicy.crossesBoundary(null, ISLAND_A, false, false));
    }

    @Test
    void failsClosedWhileEitherSideIsMigrating() {
        assertTrue(IslandAutomationBoundaryPolicy.crossesBoundary(ISLAND_A, ISLAND_A, true, false));
        assertTrue(IslandAutomationBoundaryPolicy.crossesBoundary(ISLAND_A, ISLAND_A, false, true));
        assertTrue(IslandAutomationBoundaryPolicy.crossesBoundary(null, null, true, true));
    }
}
