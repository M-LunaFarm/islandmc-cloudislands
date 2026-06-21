package kr.lunaf.cloudislands.paper.gui;

import java.util.Set;

final class GuiInventoryEventPolicy {
    private GuiInventoryEventPolicy() {
    }

    static boolean cancelClick(boolean cloudIslandsTopInventory) {
        return cloudIslandsTopInventory;
    }

    static boolean cancelDrag(boolean cloudIslandsTopInventory, Set<Integer> rawSlots, int topSize) {
        return cloudIslandsTopInventory && touchesTopInventory(rawSlots, topSize);
    }

    static boolean acceptsMenuActionSlot(boolean clickedTopInventory, int rawSlot, int topSize, GuiClick click) {
        return click != null && click.supported() && clickedTopInventory && rawSlot >= 0 && rawSlot < topSize;
    }

    private static boolean touchesTopInventory(Set<Integer> rawSlots, int topSize) {
        if (rawSlots == null || rawSlots.isEmpty() || topSize <= 0) {
            return false;
        }
        for (int rawSlot : rawSlots) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                return true;
            }
        }
        return false;
    }
}
