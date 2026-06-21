package kr.lunaf.cloudislands.paper.gui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

final class GuiEventGuard implements Listener {
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (GuiInventoryEventPolicy.cancelClick(top.getHolder() instanceof CloudIslandsMenuHolder)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof CloudIslandsMenuHolder)) {
            return;
        }
        if (GuiInventoryEventPolicy.cancelDrag(true, event.getRawSlots(), top.getSize())) {
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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        GuiSessions.invalidate(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        GuiSessions.invalidate(event.getPlayer());
    }
}
