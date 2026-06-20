package kr.lunaf.cloudislands.paper.gui;

import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

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

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CloudIslandsMenuHolder menuHolder) {
            GuiSessions.invalidate(player, menuHolder.sessionId());
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
