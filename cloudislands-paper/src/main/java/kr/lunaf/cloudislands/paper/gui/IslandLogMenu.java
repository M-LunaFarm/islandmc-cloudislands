package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

public final class IslandLogMenu implements Listener {
    private static final String TITLE = "섬 로그";
    private final MessageRenderer messages;
    private static final Set<String> INTERNAL_PAYLOAD_KEYS = Set.of(
        "activenode",
        "activeworld",
        "cellx",
        "cellz",
        "fromnode",
        "nodeid",
        "originx",
        "originz",
        "sourcenode",
        "targetnode",
        "targetservername",
        "worldname"
    );

    public IslandLogMenu() {
        this(null);
    }

    public IslandLogMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandLogs(islandId, 27)
            .thenAccept(body -> openSync(plugin, player, body, messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "log-menu-load-failed", "섬 로그를 불러오지 못했습니다.")));
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
        if (slot < 0 || slot >= 36) {
            return;
        }
        if (slot == 31) {
            player.closeInventory();
            player.performCommand("섬 로그");
            return;
        }
        if (slot == 30) {
            player.closeInventory();
            player.performCommand("섬 메뉴");
            return;
        }
        if (slot == 32) {
            player.closeInventory();
            player.performCommand("섬 설정");
            return;
        }
        if (slot == 35) {
            player.closeInventory();
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        player.sendMessage(message(messages, "log-menu-detail-title", "섬 로그 상세"));
        if (meta.hasLore() && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                player.sendMessage("- " + line);
            }
        }
    }

    private static void openSync(Plugin plugin, Player player, String body, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 36, TITLE);
            List<LogEntry> entries = logs(body);
            if (entries.isEmpty()) {
                inventory.setItem(13, item(Material.BARRIER, message(messages, "log-menu-empty-title", "로그 없음"), message(messages, "log-menu-empty", "아직 기록된 섬 로그가 없습니다.")));
            } else {
                for (int index = 0; index < entries.size() && index < 27; index++) {
                    LogEntry entry = entries.get(index);
                    inventory.setItem(index, item(material(entry.action()), (index + 1) + ". " + entry.action(), lore(entry, messages)));
                }
            }
            inventory.setItem(30, item(Material.COMPASS, message(messages, "log-menu-main-menu-name", "메인 메뉴"), message(messages, "log-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(31, item(Material.CLOCK, message(messages, "log-menu-refresh-name", "새로고침"), message(messages, "log-menu-refresh-command", "/섬 로그")));
            inventory.setItem(32, item(Material.COMPARATOR, message(messages, "log-menu-settings-name", "설정"), message(messages, "log-menu-settings-command", "/섬 설정")));
            inventory.setItem(35, item(Material.OAK_DOOR, message(messages, "log-menu-close-name", "닫기"), message(messages, "log-menu-close", "메뉴를 닫습니다.")));
            player.openInventory(inventory);
        });
    }

    private static List<String> lore(LogEntry entry, MessageRenderer messages) {
        List<String> lore = new ArrayList<>();
        lore.add(message(messages, "log-menu-time", "시간: ") + fallback(entry.createdAt(), message(messages, "log-menu-unknown", "unknown")));
        lore.add(message(messages, "log-menu-actor", "처리자: ") + shorten(entry.actorUuid()));
        if (entry.payload().isEmpty()) {
            lore.add(message(messages, "log-menu-payload-empty", "payload: 없음"));
        } else {
            for (Map.Entry<String, String> payload : entry.payload().entrySet()) {
                lore.add(payload.getKey() + ": " + payload.getValue());
                if (lore.size() >= 8) {
                    lore.add(message(messages, "log-menu-more", "..."));
                    break;
                }
            }
        }
        return lore;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static Material material(String action) {
        String normalized = action == null ? "" : action;
        if (normalized.contains("BANK")) {
            return Material.GOLD_INGOT;
        }
        if (normalized.contains("MEMBER") || normalized.contains("OWNERSHIP")) {
            return Material.PLAYER_HEAD;
        }
        if (normalized.contains("PERMISSION") || normalized.contains("FLAG") || normalized.contains("LOCK")) {
            return Material.REDSTONE_TORCH;
        }
        if (normalized.contains("SNAPSHOT") || normalized.contains("RESET")) {
            return Material.CHEST;
        }
        if (normalized.contains("CHAT")) {
            return Material.WRITABLE_BOOK;
        }
        return Material.BOOK;
    }

    private static List<LogEntry> logs(String body) {
        List<LogEntry> entries = new ArrayList<>();
        int arrayStart = body == null ? -1 : body.indexOf("\"logs\":[");
        if (arrayStart < 0) {
            return entries;
        }
        int index = body.indexOf('{', arrayStart);
        while (index >= 0 && index < body.length()) {
            int end = objectEnd(body, index);
            if (end < 0) {
                break;
            }
            String object = body.substring(index, end + 1);
            String action = text(object, "action");
            if (!action.isBlank()) {
                entries.add(new LogEntry(text(object, "actorUuid"), action, payload(object), text(object, "createdAt")));
            }
            index = body.indexOf('{', end + 1);
        }
        return entries;
    }

    private static int objectEnd(String body, int start) {
        int depth = 0;
        boolean string = false;
        boolean escaped = false;
        for (int index = start; index < body.length(); index++) {
            char current = body.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                string = !string;
                continue;
            }
            if (string) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    private static Map<String, String> payload(String object) {
        Map<String, String> values = new LinkedHashMap<>();
        String needle = "\"payload\":{";
        int start = object.indexOf(needle);
        if (start < 0) {
            return values;
        }
        start += "\"payload\":".length();
        int end = objectEnd(object, start);
        if (end < 0) {
            return values;
        }
        String payload = object.substring(start + 1, end);
        int index = 0;
        while (index < payload.length()) {
            int keyStart = payload.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = stringEnd(payload, keyStart + 1);
            int valueStart = keyEnd < 0 ? -1 : payload.indexOf('"', keyEnd + 1);
            int valueEnd = valueStart < 0 ? -1 : stringEnd(payload, valueStart + 1);
            if (keyEnd < 0 || valueStart < 0 || valueEnd < 0) {
                break;
            }
            String key = unescape(payload.substring(keyStart + 1, keyEnd));
            if (!internalPayloadKey(key)) {
                values.put(key, unescape(payload.substring(valueStart + 1, valueEnd)));
            }
            index = valueEnd + 1;
        }
        return values;
    }

    private static boolean internalPayloadKey(String key) {
        return key != null && INTERNAL_PAYLOAD_KEYS.contains(key.replace("-", "").replace("_", "").toLowerCase(java.util.Locale.ROOT));
    }

    private static int stringEnd(String value, int start) {
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                return index;
            }
        }
        return -1;
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = stringEnd(body, start);
        return end < start ? "" : unescape(body.substring(start, end));
    }

    private static ItemStack item(Material material, String name, String... lore) {
        return item(material, name, List.of(lore));
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String shorten(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= 12 ? value : value.substring(0, 8) + "...";
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private record LogEntry(String actorUuid, String action, Map<String, String> payload, String createdAt) {}
}
