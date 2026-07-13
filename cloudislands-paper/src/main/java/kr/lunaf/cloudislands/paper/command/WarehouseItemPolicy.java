package kr.lunaf.cloudislands.paper.command;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Warehouse rows store only a material key and amount, so item metadata cannot be represented safely. */
final class WarehouseItemPolicy {
    private WarehouseItemPolicy() {
    }

    static boolean storable(ItemStack item, Material material) {
        return item != null
            && material != null
            && !material.isAir()
            && item.getType() == material
            && item.getAmount() > 0
            && !item.hasItemMeta();
    }
}
