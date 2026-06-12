package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
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

public final class IslandVisitMenu implements Listener {
    private static final String TITLE = "섬 방문";

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        client.listPublicIslands(45)
            .thenAccept(body -> openSync(plugin, player, islands(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("공개 섬 목록을 불러오지 못했습니다."));
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
        if (name.equals("랜덤 공개 섬")) {
            player.performCommand("섬 랜덤방문");
            return;
        }
        if (name.equals("공개 워프 목록")) {
            player.performCommand("섬 공개워프목록");
            return;
        }
        if (name.equals("새로고침")) {
            player.performCommand("섬 방문");
            return;
        }
        if (meta.getLore() == null) {
            return;
        }
        String islandId = loreValue(meta.getLore(), "islandId=");
        if (!islandId.isBlank()) {
            player.performCommand("섬 방문 " + islandId);
        }
    }

    private static void openSync(Plugin plugin, Player player, List<IslandEntry> islands) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            inventory.setItem(4, item(Material.COMPASS, "랜덤 공개 섬", "공개된 섬 중 하나로 이동합니다."));
            if (islands.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "공개 섬 없음", "방문 가능한 공개 섬이 없습니다."));
            } else {
                for (int index = 0; index < islands.size() && index < 36; index++) {
                    IslandEntry island = islands.get(index);
                    inventory.setItem(index + 9, item(Material.GRASS_BLOCK, island.name(), "islandId=" + island.islandId(), "ownerUuid=" + island.ownerUuid(), "level=" + island.level(), "worth=" + island.worth(), "클릭하면 방문합니다."));
                }
            }
            inventory.setItem(45, item(Material.ENDER_EYE, "공개 워프 목록", "/섬 공개워프목록"));
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 방문"));
            player.openInventory(inventory);
        });
    }

    private static List<IslandEntry> islands(String body) {
        List<IslandEntry> entries = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String name = text(object, "name");
                entries.add(new IslandEntry(islandId, text(object, "ownerUuid"), name.isBlank() ? islandId : name, number(object, "level"), text(object, "worth")));
            }
            index = objectEnd + 1;
        }
        return entries;
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

    private static String loreValue(List<String> lore, String prefix) {
        for (String line : lore) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return "";
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long number(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
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

    private record IslandEntry(String islandId, String ownerUuid, String name, long level, String worth) {
    }
}
