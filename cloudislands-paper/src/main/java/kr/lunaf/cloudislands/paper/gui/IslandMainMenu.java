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

public final class IslandMainMenu implements Listener {
    private static final String TITLE = "섬 메뉴";

    public static void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(10, item(Material.GRASS_BLOCK, "내 섬으로 이동", "/섬 홈"));
        inventory.setItem(11, item(Material.OAK_SAPLING, "섬 생성", "/섬 생성"));
        inventory.setItem(12, item(Material.ENDER_PEARL, "섬 워프", "/섬 워프"));
        inventory.setItem(13, item(Material.COMPASS, "섬 방문", "명령어: /섬 방문 <플레이어|섬이름>", "우클릭: /섬 랜덤방문"));
        inventory.setItem(14, item(Material.NAME_TAG, "멤버 관리", "/섬 멤버"));
        inventory.setItem(15, item(Material.COMPARATOR, "섬 설정", "/섬 설정"));
        inventory.setItem(16, item(Material.GOLD_BLOCK, "섬 랭킹", "/섬 랭킹"));
        inventory.setItem(20, item(Material.EMERALD, "섬 은행", "/섬 은행"));
        inventory.setItem(21, item(Material.BOOK, "미션", "/섬 미션"));
        inventory.setItem(23, item(Material.BEACON, "업그레이드", "/섬 업그레이드"));
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
        if (name.equals("내 섬으로 이동")) {
            player.performCommand("섬 홈");
        } else if (name.equals("섬 생성")) {
            player.performCommand("섬 생성메뉴");
        } else if (name.equals("섬 워프")) {
            player.performCommand("섬 워프");
        } else if (name.equals("섬 방문")) {
            if (event.isRightClick()) {
                player.performCommand("섬 랜덤방문");
            } else {
                player.sendMessage("사용법: /섬 방문 <플레이어|섬이름>");
            }
        } else if (name.equals("멤버 관리")) {
            player.performCommand("섬 멤버관리");
        } else if (name.equals("섬 설정")) {
            player.performCommand("섬 설정");
        } else if (name.equals("섬 랭킹")) {
            player.performCommand("섬 랭킹");
        } else if (name.equals("섬 은행")) {
            player.performCommand("섬 은행");
        } else if (name.equals("미션")) {
            player.performCommand("섬 미션");
        } else if (name.equals("업그레이드")) {
            player.performCommand("섬 업그레이드");
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
