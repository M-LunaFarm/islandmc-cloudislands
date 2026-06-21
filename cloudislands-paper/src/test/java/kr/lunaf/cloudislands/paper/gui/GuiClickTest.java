package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GuiClickTest {
    @Test
    void mapsOnlyExplicitLeftAndRightClickModesToSupportedActions() {
        assertEquals(GuiClick.LEFT, GuiClickPolicy.fromClickName("LEFT"));
        assertEquals(GuiClick.RIGHT, GuiClickPolicy.fromClickName("RIGHT"));
        assertEquals(GuiClick.SHIFT_LEFT, GuiClickPolicy.fromClickName("SHIFT_LEFT"));
        assertEquals(GuiClick.SHIFT_RIGHT, GuiClickPolicy.fromClickName("SHIFT_RIGHT"));

        for (String click : new String[] {
            "NUMBER_KEY",
            "DOUBLE_CLICK",
            "DROP",
            "CONTROL_DROP",
            "SWAP_OFFHAND",
            "CREATIVE",
            "UNKNOWN",
            "",
            null
        }) {
            assertEquals(GuiClick.UNSUPPORTED, GuiClickPolicy.fromClickName(click));
            assertFalse(GuiClickPolicy.fromClickName(click).supported());
        }
    }

    @Test
    void supportedClickHelpersExposeDirectionAndShiftState() {
        assertTrue(GuiClick.LEFT.left());
        assertFalse(GuiClick.LEFT.right());
        assertFalse(GuiClick.LEFT.shift());

        assertTrue(GuiClick.SHIFT_RIGHT.right());
        assertTrue(GuiClick.SHIFT_RIGHT.shift());
        assertFalse(GuiClick.UNSUPPORTED.supported());
    }
}
