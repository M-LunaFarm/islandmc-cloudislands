package kr.lunaf.cloudislands.paper.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class GuiInventories {
    private GuiInventories() {
    }

    public static Inventory create(String menuId, int size, String title) {
        CloudIslandsMenuHolder holder = new CloudIslandsMenuHolder(menuId);
        Inventory inventory = Bukkit.createInventory(holder, size, title);
        holder.attach(inventory);
        return inventory;
    }

    public static boolean isMenu(Inventory inventory, String menuId) {
        if (inventory == null) {
            return false;
        }
        InventoryHolder holder = inventory.getHolder();
        return holder instanceof CloudIslandsMenuHolder menuHolder && menuHolder.menuId().equals(menuId);
    }
}
