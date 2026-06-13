package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

public final class IslandLimitMenu implements Listener {
    private static final String TITLE = "섬 제한";
    private final MessageRenderer messages;

    public IslandLimitMenu() {
        this(null);
    }

    public IslandLimitMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandLimits(islandId)
            .thenAccept(body -> openSync(plugin, player, limits(body), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "limit-menu-load-failed", "섬 제한을 불러오지 못했습니다.")));
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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 45) {
            player.performCommand("섬 메뉴");
            return;
        }
        if (slot == 49) {
            player.performCommand("섬 제한");
            return;
        }
        if (slot == 53) {
            player.performCommand("섬 설정");
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return;
        }
        String limitKey = loreValue(meta, "제한 키=");
        if (limitKey.isBlank()) {
            return;
        }
        long value = number(loreValue(meta, "limitValue="));
        long step = event.isShiftClick() ? 10L : 1L;
        long nextValue = event.isRightClick() ? Math.max(0L, value - step) : value + step;
        player.performCommand("섬 제한설정 " + limitKey + " " + nextValue);
    }

    private static void openSync(Plugin plugin, Player player, List<Limit> limits, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            int slot = 0;
            for (Limit limit : limits.stream().limit(45).toList()) {
                inventory.setItem(slot++, limitItem(limit, messages));
            }
            if (limits.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "limit-menu-empty-title", "제한 없음"), message(messages, "limit-menu-empty", "현재 설정된 섬 제한이 없습니다.")));
            }
            inventory.setItem(45, item(Material.COMPASS, message(messages, "limit-menu-main-menu-name", "메인 메뉴"), message(messages, "limit-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "limit-menu-refresh-name", "새로고침"), message(messages, "limit-menu-refresh-command", "/섬 제한")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "limit-menu-settings-name", "설정"), message(messages, "limit-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack limitItem(Limit limit, MessageRenderer messages) {
        return item(Material.HOPPER, limit.key(), "제한 키=" + limit.key(), "limitValue=" + limit.value(), message(messages, "limit-menu-current-value", "현재 값: ") + limit.value(), limit.updatedAt().isBlank() ? message(messages, "limit-menu-no-update", "업데이트 정보 없음") : message(messages, "limit-menu-updated-at", "갱신 시각: ") + limit.updatedAt(), message(messages, "limit-menu-left-click", "좌클릭: +1"), message(messages, "limit-menu-right-click", "우클릭: -1"), message(messages, "limit-menu-shift-click", "Shift+클릭: 10 단위로 조정"));
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
