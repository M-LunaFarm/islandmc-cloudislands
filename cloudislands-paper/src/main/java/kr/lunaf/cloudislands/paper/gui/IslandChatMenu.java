package kr.lunaf.cloudislands.paper.gui;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class IslandChatMenu implements Listener {
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/chat.yml",
        new GuiMenuDefinition("island.chat", 3, "chat-menu-title", java.util.Map.of(
            "logs", "island.logs.open",
            "settings", "island.settings.open",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final String TITLE = "섬 채팅";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandChatMenu() {
        this(null);
    }

    public IslandChatMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandChatMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(MENU, messages, TITLE);
        player.openInventory(inventory);
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
        if (slot < 0 || slot >= MENU.size()) {
            return;
        }
        GuiMenuDefinition.MenuItem menuItem = MENU.itemAt(slot).orElse(null);
        if (menuItem == null) {
            return;
        }
        player.closeInventory();
        if (menuItem.symbol().equals("I")) {
            player.sendMessage(message(messages, "chat-menu-island-usage", "사용법: /섬 채팅 <메시지>"));
        } else if (menuItem.symbol().equals("T")) {
            player.sendMessage(message(messages, "chat-menu-team-usage", "사용법: /섬 팀채팅 <메시지>"));
        } else if (!menuItem.actionKey().isBlank()) {
            actions.execute(player, GuiActions.from(MENU.action(menuItem.actionKey(), "")).orElse(null), GuiClick.from(event));
        }
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
