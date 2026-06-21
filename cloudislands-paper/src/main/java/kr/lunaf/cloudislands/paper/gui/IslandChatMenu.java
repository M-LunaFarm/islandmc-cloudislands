package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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
        Inventory inventory = GuiInventories.create(MENU_ID, MENU.size(), message(messages, MENU.titleKey(), TITLE));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int currentSlot = slot;
            MENU.itemAt(slot).ifPresent(item -> inventory.setItem(currentSlot, item(item, messages)));
        }
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
        if (slot < 0 || slot >= 27) {
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
            actions.execute(player, MENU.action(menuItem.actionKey(), ""), GuiClick.from(event));
        }
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack item(GuiMenuDefinition.MenuItem definition, MessageRenderer messages) {
        ItemStack item = new ItemStack(material(definition.materialKey()));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(message(messages, definition.nameKey(), definition.fallbackName()));
            meta.setLore(lore(definition, messages));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static List<String> lore(GuiMenuDefinition.MenuItem definition, MessageRenderer messages) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        int count = Math.max(definition.loreKeys().size(), definition.fallbackLore().size());
        for (int index = 0; index < count; index++) {
            String key = index < definition.loreKeys().size() ? definition.loreKeys().get(index) : "";
            String fallback = index < definition.fallbackLore().size() ? definition.fallbackLore().get(index) : "";
            lore.add(message(messages, key, fallback));
        }
        return List.copyOf(lore);
    }

    private static Material material(String key) {
        try {
            return Material.valueOf(key == null ? "STONE" : key);
        } catch (RuntimeException ignored) {
            return Material.STONE;
        }
    }
}
