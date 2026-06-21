package kr.lunaf.cloudislands.paper.gui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class GuiInventoryEventPolicyTest {
    @Test
    void cloudMenuClickGuardCancelsEveryClickWhileMenuIsOpen() {
        assertTrue(GuiInventoryEventPolicy.cancelClick(true));
        assertFalse(GuiInventoryEventPolicy.cancelClick(false));
    }

    @Test
    void menuActionsOnlyExecuteForTopInventorySlots() {
        assertTrue(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, 0, 54, GuiClick.LEFT));
        assertTrue(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, 53, 54, GuiClick.RIGHT));

        assertFalse(GuiInventoryEventPolicy.acceptsMenuActionSlot(false, 0, 54, GuiClick.LEFT));
        assertFalse(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, -1, 54, GuiClick.LEFT));
        assertFalse(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, 54, 54, GuiClick.LEFT));
        assertFalse(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, 80, 54, GuiClick.LEFT));
        assertFalse(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, 0, 54, GuiClick.UNSUPPORTED));
        assertFalse(GuiInventoryEventPolicy.acceptsMenuActionSlot(true, 0, 54, null));
    }

    @Test
    void dragGuardCancelsOnlyWhenDragTouchesCloudMenuTopInventory() {
        assertTrue(GuiInventoryEventPolicy.cancelDrag(true, Set.of(0, 60), 54));
        assertTrue(GuiInventoryEventPolicy.cancelDrag(true, Set.of(53), 54));

        assertFalse(GuiInventoryEventPolicy.cancelDrag(false, Set.of(0), 54));
        assertFalse(GuiInventoryEventPolicy.cancelDrag(true, Set.of(54, 60), 54));
        assertFalse(GuiInventoryEventPolicy.cancelDrag(true, Set.of(-1, 54), 54));
        assertFalse(GuiInventoryEventPolicy.cancelDrag(true, Set.of(0), 0));
        assertFalse(GuiInventoryEventPolicy.cancelDrag(true, Set.of(), 54));
        assertFalse(GuiInventoryEventPolicy.cancelDrag(true, null, 54));
    }
}
