package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
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

public final class IslandRankingMenu implements Listener {
    private static final String TITLE = "섬 랭킹";
    private final MessageRenderer messages;

    public IslandRankingMenu() {
        this(null);
    }

    public IslandRankingMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        CompletableFuture<String> level = client.topIslandsByLevel(10);
        CompletableFuture<String> worth = client.topIslandsByWorth(10);
        level.thenCombine(worth, RankingData::new)
            .thenAccept(data -> openSync(plugin, player, rankings(data.levelBody(), "level"), rankings(data.worthBody(), "worth"), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "ranking-menu-load-failed", "섬 랭킹을 불러오지 못했습니다.")));
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
        if (meta == null || meta.getLore() == null) {
            return;
        }
        String displayName = meta.getDisplayName();
        player.closeInventory();
        if ("공개 섬".equals(displayName)) {
            player.performCommand("섬 방문");
            return;
        }
        if ("랜덤 방문".equals(displayName)) {
            player.performCommand("섬 랜덤방문");
            return;
        }
        if ("새로고침".equals(displayName)) {
            player.performCommand("섬 랭킹");
            return;
        }
        String islandId = loreValue(meta, "섬 ID=");
        if (islandId.isBlank()) {
            return;
        }
        player.performCommand("섬 방문 " + islandId);
    }

    private static void openSync(Plugin plugin, Player player, List<Ranking> levels, List<Ranking> worths, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            inventory.setItem(4, item(Material.GOLD_BLOCK, "섬 랭킹", "좌측: 레벨 TOP", "우측: 가치 TOP"));
            int slot = 9;
            for (Ranking ranking : levels) {
                inventory.setItem(slot++, rankingItem(Material.EXPERIENCE_BOTTLE, ranking, messages));
            }
            slot = 27;
            for (Ranking ranking : worths) {
                inventory.setItem(slot++, rankingItem(Material.EMERALD, ranking, messages));
            }
            inventory.setItem(45, item(Material.COMPASS, "공개 섬", "/섬 방문"));
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 랭킹"));
            inventory.setItem(53, item(Material.ENDER_PEARL, "랜덤 방문", "/섬 랜덤방문"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack rankingItem(Material material, Ranking ranking, MessageRenderer messages) {
        return item(material, ranking.label() + " #" + ranking.rank(), "섬 ID=" + ranking.islandId(), "레벨: " + ranking.level(), "가치: " + ranking.worth(), message(messages, "ranking-menu-click-to-visit", "클릭하면 방문을 시도합니다."));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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

    private static List<Ranking> rankings(String body, String label) {
        List<Ranking> rankings = new ArrayList<>();
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
                rankings.add(new Ranking(rankings.size() + 1, label, islandId, number(object, "level"), text(object, "worth")));
            }
            index = objectEnd + 1;
        }
        return rankings;
    }

    private static String loreValue(ItemMeta meta, String prefix) {
        for (String line : meta.getLore()) {
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

    private record RankingData(String levelBody, String worthBody) {
    }

    private record Ranking(int rank, String label, String islandId, long level, String worth) {
    }
}
