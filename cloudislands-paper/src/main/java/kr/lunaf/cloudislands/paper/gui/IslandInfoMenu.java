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

public final class IslandInfoMenu implements Listener {
    private static final String TITLE = "섬 정보";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.islandInfo(islandId)
            .thenAccept(body -> openSync(plugin, player, body))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 정보를 불러오지 못했습니다."));
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
        if (name.equals("레벨 다시 계산")) {
            player.performCommand("섬 레벨계산");
        } else if (name.equals("섬 랭킹")) {
            player.performCommand("섬 랭킹");
        } else if (name.equals("섬 로그")) {
            player.performCommand("섬 로그");
        } else if (name.equals("설정")) {
            player.performCommand("섬 설정");
        } else if (name.equals("새로고침")) {
            player.performCommand("섬 정보");
        } else if (name.equals("메인 메뉴")) {
            player.performCommand("섬 메뉴");
        } else if (name.equals("닫기")) {
            return;
        }
    }

    private static void openSync(Plugin plugin, Player player, String body) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
            inventory.setItem(10, item(Material.GRASS_BLOCK, "기본 정보", "name=" + fallback(text(body, "name"), "unknown"), "state=" + fallback(text(body, "state"), "unknown"), "islandId=" + text(body, "islandId")));
            inventory.setItem(11, item(Material.EXPERIENCE_BOTTLE, "레벨", "level=" + number(body, "level"), "worth=" + fallback(text(body, "worth"), "0")));
            inventory.setItem(12, item(Material.BARRIER, "공개 상태", "publicAccess=" + bool(body, "publicAccess"), "locked=" + bool(body, "locked")));
            inventory.setItem(13, item(Material.MAP, "크기와 경계", "size=" + number(body, "size"), "border=" + number(body, "border")));
            inventory.setItem(14, item(Material.PLAYER_HEAD, "소유자", "ownerUuid=" + text(body, "ownerUuid")));
            inventory.setItem(16, item(Material.REDSTONE_TORCH, "설정", "/섬 설정"));
            inventory.setItem(21, item(Material.GOLD_BLOCK, "섬 랭킹", "/섬 랭킹"));
            inventory.setItem(22, item(Material.CLOCK, "섬 로그", "/섬 로그"));
            inventory.setItem(23, item(Material.ANVIL, "레벨 다시 계산", "/섬 레벨계산"));
            inventory.setItem(24, item(Material.COMPASS, "메인 메뉴", "/섬 메뉴"));
            inventory.setItem(25, item(Material.CLOCK, "새로고침", "/섬 정보"));
            inventory.setItem(26, item(Material.OAK_DOOR, "닫기", "메뉴를 닫습니다."));
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

    private static long number(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static boolean bool(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body == null ? -1 : body.indexOf(needle);
        return start >= 0 && body.startsWith("true", start + needle.length());
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
