package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class IslandSettingsMenu implements Listener {
    private static final String TITLE = "섬 설정";

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(10, item(Material.LIME_DYE, "공개 설정", "좌클릭: /섬 공개", "우클릭: /섬 비공개"));
        inventory.setItem(11, item(Material.OAK_DOOR, "잠금 설정", "좌클릭: /섬 잠금해제", "우클릭: /섬 잠금"));
        inventory.setItem(12, item(Material.NAME_TAG, "멤버 관리", "/섬 멤버"));
        inventory.setItem(13, item(Material.COMPARATOR, "권한 설정", "/섬 권한"));
        inventory.setItem(14, item(Material.REDSTONE_TORCH, "플래그 설정", "/섬 플래그"));
        inventory.setItem(15, item(Material.ENDER_PEARL, "워프 관리", "/섬 워프"));
        inventory.setItem(16, item(Material.BARRIER, "방문자 밴", "/섬 밴목록"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String name = meta.getDisplayName();
        player.closeInventory();
        if (name.equals("공개 설정")) {
            player.performCommand(event.isRightClick() ? "섬 비공개" : "섬 공개");
        } else if (name.equals("잠금 설정")) {
            player.performCommand(event.isRightClick() ? "섬 잠금" : "섬 잠금해제");
        } else if (name.equals("멤버 관리")) {
            player.performCommand("섬 멤버");
        } else if (name.equals("권한 설정")) {
            player.performCommand("섬 권한");
        } else if (name.equals("플래그 설정")) {
            player.performCommand("섬 플래그");
        } else if (name.equals("워프 관리")) {
            player.performCommand("섬 워프");
        } else if (name.equals("방문자 밴")) {
            player.performCommand("섬 밴목록");
        }
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
