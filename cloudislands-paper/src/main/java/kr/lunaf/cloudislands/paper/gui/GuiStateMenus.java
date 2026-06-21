package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class GuiStateMenus implements Listener {
    public static final String MENU_ID = "gui.state";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/state.yml",
        new GuiMenuDefinition(MENU_ID, 3, "gui-state-title", java.util.Map.of(
            "retry", "gui.close",
            "back", "gui.close"
        ))
    );
    private final GuiActionRegistry actions;

    private GuiStateMenus(GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static GuiStateMenus listener() {
        return listener(new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public static GuiStateMenus listener(GuiActionRegistry actions) {
        return new GuiStateMenus(actions);
    }

    public static GuiSession openLoading(Plugin plugin, Player player, MessageRenderer messages, String title) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        openLoading(plugin, player, session, messages, title);
        return session;
    }

    public static void openLoading(Plugin plugin, Player player, GuiSession session, MessageRenderer messages, String title) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            if (!GuiSessions.isCurrent(player, session)) {
                return;
            }
            Inventory inventory = stateInventory(session.sessionId(), messages, title, "Loading", false, false);
            inventory.setItem(13, stateItem(Material.CLOCK, message(messages, "gui-state-loading-name", "Loading"), message(messages, "gui-state-loading-lore", "Core 응답을 기다리는 중입니다.")));
            player.openInventory(inventory);
        });
    }

    public static void openError(Plugin plugin, Player player, GuiSession session, MessageRenderer messages, String title, String detail, String retryAction, String backAction) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> openErrorSync(player, session.sessionId(), messages, title, detail, retryAction, backAction));
    }

    public static void openError(Plugin plugin, Player player, MessageRenderer messages, String title, String detail, String retryAction, String backAction) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            openErrorSync(player, null, messages, title, detail, retryAction, backAction);
        });
    }

    public static void openSaving(Plugin plugin, Player player, MessageRenderer messages, String title) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = stateInventory(null, messages, title, "Saving", false, false);
            inventory.setItem(13, stateItem(Material.HOPPER, message(messages, "gui-state-saving-name", "Saving"), message(messages, "gui-state-saving-lore", "변경 사항을 Core에 저장하는 중입니다.")));
            player.openInventory(inventory);
        });
    }

    public static void openSuccess(Plugin plugin, Player player, MessageRenderer messages, String title, String detail, String backAction) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = stateInventory(null, messages, title, "Success", false, backAction != null && !backAction.isBlank());
            inventory.setItem(13, stateItem(Material.EMERALD_BLOCK, message(messages, "gui-state-success-name", "Success"), detail == null || detail.isBlank() ? message(messages, "gui-state-success-lore", "요청이 완료되었습니다.") : detail));
            if (backAction != null && !backAction.isBlank()) {
                setStateAction(inventory, 15, messages, backAction);
            }
            player.openInventory(inventory);
        });
    }

    public static void openConflict(Plugin plugin, Player player, MessageRenderer messages, String title, String detail, String retryAction, String backAction) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = stateInventory(null, messages, title, "Conflict", retryAction != null && !retryAction.isBlank(), backAction != null && !backAction.isBlank());
            inventory.setItem(13, stateItem(Material.ANVIL, message(messages, "gui-state-conflict-name", "Conflict"), detail == null || detail.isBlank() ? message(messages, "gui-state-conflict-lore", "다른 관리자가 먼저 변경했습니다.") : detail));
            if (retryAction != null && !retryAction.isBlank()) {
                setStateAction(inventory, 11, messages, retryAction);
            }
            if (backAction != null && !backAction.isBlank()) {
                setStateAction(inventory, 15, messages, backAction);
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
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), GuiClick.from(event));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CloudIslandsMenuHolder menuHolder) {
            GuiSessions.invalidate(player, menuHolder.sessionId());
        }
    }

    private static void openErrorSync(Player player, java.util.UUID sessionId, MessageRenderer messages, String title, String detail, String retryAction, String backAction) {
        Inventory inventory = stateInventory(sessionId, messages, title, "Error", retryAction != null && !retryAction.isBlank(), backAction != null && !backAction.isBlank());
        inventory.setItem(13, stateItem(Material.BARRIER, message(messages, "gui-state-error-name", "Error"), detail == null || detail.isBlank() ? message(messages, "gui-state-error-lore", "요청을 처리하지 못했습니다.") : detail));
        if (retryAction != null && !retryAction.isBlank()) {
            setStateAction(inventory, 11, messages, retryAction);
        }
        if (backAction != null && !backAction.isBlank()) {
            setStateAction(inventory, 15, messages, backAction);
        }
        player.openInventory(inventory);
    }

    private static Inventory stateInventory(UUID sessionId, MessageRenderer messages, String title, String fallback, boolean retry, boolean back) {
        Inventory inventory = sessionId == null
            ? GuiInventories.create(MENU.id(), MENU.size(), title(messages, title, fallback))
            : GuiInventories.create(MENU.id(), sessionId, MENU.size(), title(messages, title, fallback));
        GuiMenuRenderer.populate(inventory, MENU, messages, item -> item.symbol().equals("I") || (retry && item.symbol().equals("R")) || (back && item.symbol().equals("B")));
        return inventory;
    }

    private static void setStateAction(Inventory inventory, int slot, MessageRenderer messages, String actionId) {
        MENU.itemAt(slot).ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, java.util.Map.of(), List.of(), actionId)));
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
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
