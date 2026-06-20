package kr.lunaf.cloudislands.paper.gui;

import java.util.Set;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

final class GuiEventGuard implements Listener {
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CloudIslandsMenuHolder)) {
            return;
        }
        if (touchesTopInventory(event.getRawSlots(), top.getSize())) {
            event.setCancelled(true);
        }
    }

    private static boolean touchesTopInventory(Set<Integer> rawSlots, int topSize) {
        for (int rawSlot : rawSlots) {
            if (rawSlot >= 0 && rawSlot < topSize) {
                return true;
            }
        }
        return false;
    }
}
