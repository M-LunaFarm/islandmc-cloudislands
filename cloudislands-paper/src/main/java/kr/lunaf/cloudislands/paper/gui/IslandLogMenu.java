package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.LogEntryView;
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
    private static final String MENU_ID = "island.logs";
    private static final String TITLE = "섬 로그";
    private final MessageRenderer messages;

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
        PaperGuiViews.islandLogs(client, islandId, 27)
            .thenAccept(entries -> openSync(plugin, player, entries, messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "log-menu-load-failed", "섬 로그를 불러오지 못했습니다.")));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU_ID)) {
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
            GuiActionRegistry.execute(player, "island.logs.open", GuiClick.from(event));
            return;
        }
        if (slot == 30) {
            player.closeInventory();
            GuiActionRegistry.execute(player, "island.main.open", GuiClick.from(event));
            return;
        }
        if (slot == 32) {
            player.closeInventory();
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
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

    private static void openSync(Plugin plugin, Player player, List<LogEntryView> entries, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 36, TITLE);
            if (entries.isEmpty()) {
                inventory.setItem(13, item(Material.BARRIER, message(messages, "log-menu-empty-title", "로그 없음"), message(messages, "log-menu-empty", "아직 기록된 섬 로그가 없습니다.")));
            } else {
                for (int index = 0; index < entries.size() && index < 27; index++) {
                    LogEntryView entry = entries.get(index);
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

    private static List<String> lore(LogEntryView entry, MessageRenderer messages) {
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

}
