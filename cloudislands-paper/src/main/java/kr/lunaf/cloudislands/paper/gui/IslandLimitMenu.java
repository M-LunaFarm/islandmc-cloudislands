package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
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

public final class IslandLimitMenu implements Listener {
    private static final String TITLE = "섬 제한";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandLimits(islandId)
            .thenAccept(body -> openSync(plugin, player, limits(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 제한을 불러오지 못했습니다."));
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
        if ("메인 메뉴".equals(displayName)) {
            player.performCommand("섬 메뉴");
            return;
        }
        if ("새로고침".equals(displayName)) {
            player.performCommand("섬 제한");
            return;
        }
        if ("설정".equals(displayName)) {
            player.performCommand("섬 설정");
            return;
        }
        String limitKey = loreValue(meta, "제한 키=");
        if (limitKey.isBlank()) {
            return;
        }
        long value = number(loreValue(meta, "현재 값: "));
        long step = event.isShiftClick() ? 10L : 1L;
        long nextValue = event.isRightClick() ? Math.max(0L, value - step) : value + step;
        player.performCommand("섬 제한설정 " + limitKey + " " + nextValue);
    }

    private static void openSync(Plugin plugin, Player player, List<Limit> limits) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            int slot = 0;
            for (Limit limit : limits.stream().limit(45).toList()) {
                inventory.setItem(slot++, limitItem(limit));
            }
            if (limits.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "제한 없음", "현재 설정된 섬 제한이 없습니다."));
            }
            inventory.setItem(45, item(Material.COMPASS, "메인 메뉴", "/섬 메뉴"));
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 제한"));
            inventory.setItem(53, item(Material.COMPARATOR, "설정", "/섬 설정"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack limitItem(Limit limit) {
        return item(Material.HOPPER, limit.key(), "제한 키=" + limit.key(), "현재 값: " + limit.value(), limit.updatedAt().isBlank() ? "업데이트 정보 없음" : "갱신 시각: " + limit.updatedAt(), "좌클릭: +1", "우클릭: -1", "Shift+클릭: 10 단위로 조정");
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

    private static List<Limit> limits(String body) {
        List<Limit> limits = new ArrayList<>();
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
            String key = text(object, "limitKey");
            if (!key.isBlank()) {
                limits.add(new Limit(key, number(object, "value"), text(object, "updatedAt")));
            }
            index = objectEnd + 1;
        }
        return limits;
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

    private static long number(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private record Limit(String key, long value, String updatedAt) {
    }
}
