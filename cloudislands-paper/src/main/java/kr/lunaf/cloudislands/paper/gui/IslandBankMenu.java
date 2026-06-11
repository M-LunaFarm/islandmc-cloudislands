package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class IslandBankMenu implements Listener {
    private static final String TITLE = "섬 은행";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.islandBank(islandId)
            .thenAccept(body -> openSync(plugin, player, text(body, "balance"), text(body, "updatedAt")))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 은행을 불러오지 못했습니다."));
                return null;
            });
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
        if (name.equals("입금")) {
            player.sendMessage("사용법: /섬 입금 <금액>");
        } else if (name.equals("출금")) {
            player.sendMessage("사용법: /섬 출금 <금액>");
        } else if (name.equals("잔액 새로고침")) {
            player.performCommand("섬 은행");
        }
    }

    private static void openSync(Plugin plugin, Player player, String balance, String updatedAt) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
            inventory.setItem(11, item(Material.GOLD_BLOCK, "잔액", "balance=" + (balance.isBlank() ? "0" : balance), updatedAt.isBlank() ? "업데이트 정보 없음" : "updatedAt=" + updatedAt));
            inventory.setItem(13, item(Material.EMERALD, "입금", "사용법: /섬 입금 <금액>"));
            inventory.setItem(15, item(Material.REDSTONE, "출금", "사용법: /섬 출금 <금액>"));
            inventory.setItem(22, item(Material.CLOCK, "잔액 새로고침", "/섬 은행"));
            player.openInventory(inventory);
        });
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

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
