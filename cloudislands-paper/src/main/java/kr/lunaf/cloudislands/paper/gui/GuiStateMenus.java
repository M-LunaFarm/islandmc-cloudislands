package kr.lunaf.cloudislands.paper.gui;

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

public final class GuiStateMenus implements Listener {
    public static final String MENU_ID = "gui.state";

    private GuiStateMenus() {
    }

    public static GuiStateMenus listener() {
        return new GuiStateMenus();
    }

    public static void openLoading(Plugin plugin, Player player, MessageRenderer messages, String title) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, title(messages, title, "Loading"));
            inventory.setItem(13, stateItem(Material.CLOCK, message(messages, "gui-state-loading-name", "Loading"), message(messages, "gui-state-loading-lore", "Core 응답을 기다리는 중입니다.")));
            player.openInventory(inventory);
        });
    }

    public static void openError(Plugin plugin, Player player, MessageRenderer messages, String title, String detail, String retryAction, String backAction) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 27, title(messages, title, "Error"));
            inventory.setItem(13, stateItem(Material.BARRIER, message(messages, "gui-state-error-name", "Error"), detail == null || detail.isBlank() ? message(messages, "gui-state-error-lore", "요청을 처리하지 못했습니다.") : detail));
            if (retryAction != null && !retryAction.isBlank()) {
                inventory.setItem(11, GuiItems.action(Material.CLOCK, message(messages, "gui-state-retry-name", "Retry"), retryAction));
            }
            if (backAction != null && !backAction.isBlank()) {
                inventory.setItem(15, GuiItems.action(Material.OAK_DOOR, message(messages, "gui-state-back-name", "Back"), backAction));
            }
            player.openInventory(inventory);
        });
    }

    public static ItemStack empty(MessageRenderer messages, String title, String detail) {
        return stateItem(Material.BARRIER, title == null || title.isBlank() ? message(messages, "gui-state-empty-name", "Empty") : title, detail == null || detail.isBlank() ? message(messages, "gui-state-empty-lore", "표시할 항목이 없습니다.") : detail);
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        GuiActionRegistry.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    private static ItemStack stateItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String title(MessageRenderer messages, String title, String fallback) {
        return title == null || title.isBlank() ? message(messages, "gui-state-title-" + fallback.toLowerCase(java.util.Locale.ROOT), fallback) : title;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }
}
