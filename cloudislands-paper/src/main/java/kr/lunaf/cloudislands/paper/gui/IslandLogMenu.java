package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.LogEntryView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
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
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/logs.yml",
        new GuiMenuDefinition("island.logs", 4, "menu.logs.title", Map.of(
            "open", "island.logs.open",
            "list", "island.logs.list",
            "detail", "island.log.detail",
            "main", "island.main.open",
            "settings", "island.settings.open",
            "back", "island.chat.open",
            "close", "gui.close"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandLogMenu() {
        this(null);
    }

    public IslandLogMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandLogMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandLogs(client, islandId, 27)
            .thenAccept(entries -> openSync(plugin, player, session, entries, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "log-menu-load-failed", "섬 로그를 불러오지 못했습니다."), "island.logs.open", "island.settings.open");
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.equals("gui.close")) {
            player.closeInventory();
            return;
        }
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, List<LogEntryView> entries, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            if (entries.isEmpty()) {
                inventory.setItem(13, item(Material.BARRIER, message(messages, "log-menu-empty-title", "로그 없음"), message(messages, "log-menu-empty", "아직 기록된 섬 로그가 없습니다.")));
            } else {
                for (int index = 0; index < entries.size() && index < 27; index++) {
                    LogEntryView entry = entries.get(index);
                    inventory.setItem(index, logItem(entry, index, messages));
                }
            }
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

    private static ItemStack logItem(LogEntryView entry, int index, MessageRenderer messages) {
        return GuiItems.action(material(entry.action()), (index + 1) + ". " + entry.action(), "island.log.detail",
            Map.of(
                "action", fallback(entry.action(), "unknown"),
                "actorUuid", fallback(entry.actorUuid(), ""),
                "createdAt", fallback(entry.createdAt(), ""),
                "payload", payloadSummary(entry.payload(), messages)
            ),
            lore(entry, messages).toArray(String[]::new));
    }

    private static String payloadSummary(Map<String, String> payload, MessageRenderer messages) {
        if (payload.isEmpty()) {
            return message(messages, "log-menu-payload-empty", "없음");
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            if (builder.length() > 180) {
                return builder.substring(0, 180) + "...";
            }
        }
        return builder.toString();
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
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
