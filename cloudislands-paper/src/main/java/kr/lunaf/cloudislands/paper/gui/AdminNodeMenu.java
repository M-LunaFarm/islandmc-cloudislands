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

public final class AdminNodeMenu implements Listener {
    private static final String TITLE = "섬 노드 관리";

    public static void open(Player player, String nodeId) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(10, item(Material.COMPASS, "노드 목록", "/ciadmin node list"));
        inventory.setItem(11, item(Material.ENDER_EYE, "현재 노드 정보", "/ciadmin node info " + nodeId));
        inventory.setItem(13, item(Material.REDSTONE_TORCH, "현재 노드 Drain", "/ciadmin node drain " + nodeId));
        inventory.setItem(14, item(Material.LEVER, "현재 노드 Undrain", "/ciadmin node undrain " + nodeId));
        inventory.setItem(15, item(Material.HOPPER, "장애 스윕", "/ciadmin node sweep " + nodeId));
        inventory.setItem(16, item(Material.MAP, "활성 섬 조회", "/ciadmin island where <uuid>", "섬 UUID로 현재 위치 노드를 확인합니다."));
        inventory.setItem(17, item(Material.MINECART, "부하 이동", "/ciadmin island migrate <uuid> <node>", "섬 UUID와 대상 노드를 입력해 이동합니다."));
        inventory.setItem(22, item(Material.BOOK, "관리 명령 도움말", "/ciadmin node list", "/ciadmin island where <uuid>"));
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
        String command = firstCommand(meta);
        String name = meta.getDisplayName();
        player.closeInventory();
        if (command != null) {
            player.performCommand(command.substring(1));
        } else if (name.equals("관리 명령 도움말")) {
            player.sendMessage("사용법: /ciadmin node list, /ciadmin node info [node], /ciadmin island where <uuid>");
        }
    }

    private static String firstCommand(ItemMeta meta) {
        if (!meta.hasLore() || meta.getLore() == null) {
            return null;
        }
        return meta.getLore().stream()
            .filter(line -> line.startsWith("/"))
            .findFirst()
            .orElse(null);
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
