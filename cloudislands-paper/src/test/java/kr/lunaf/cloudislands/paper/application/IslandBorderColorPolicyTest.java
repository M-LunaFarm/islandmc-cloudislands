package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IslandBorderColorPolicyTest {
    @Test
    void mapsClientBorderColorsToStableLongRunningTransitions() {
        var blue = IslandBorderColorPolicy.transition(300.0D, "blue");
        var green = IslandBorderColorPolicy.transition(300.0D, "green");
        var red = IslandBorderColorPolicy.transition(300.0D, "red");

        assertFalse(blue.animated());
        assertTrue(green.initialSize() < green.targetSize());
        assertTrue(red.initialSize() > red.targetSize());
        assertEquals(IslandBorderColorPolicy.COLOR_TRANSITION_TICKS, green.durationTicks());
        assertEquals(IslandBorderColorPolicy.COLOR_TRANSITION_TICKS, red.durationTicks());
    }

    @Test
    void normalizesUnsupportedColorsToBlueWithoutFakeVisualClaims() {
        assertEquals("blue", IslandBorderColorPolicy.transition(0.0D, "purple").color());
        assertEquals(1.0D, IslandBorderColorPolicy.transition(0.0D, "purple").initialSize());
    }
}
