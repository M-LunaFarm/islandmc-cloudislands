package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
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
            setStateItem(inventory, "L", messages, null);
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
            setStateItem(inventory, "S", messages, null);
            player.openInventory(inventory);
        });
    }

    public static void openSuccess(Plugin plugin, Player player, MessageRenderer messages, String title, String detail, String backAction) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = stateInventory(null, messages, title, "Success", false, backAction != null && !backAction.isBlank());
            setStateItem(inventory, "U", messages, detail);
            if (backAction != null && !backAction.isBlank()) {
                setStateAction(inventory, "B", messages, backAction);
            }
            player.openInventory(inventory);
        });
    }

    public static void openConflict(Plugin plugin, Player player, MessageRenderer messages, String title, String detail, String retryAction, String backAction) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = stateInventory(null, messages, title, "Conflict", retryAction != null && !retryAction.isBlank(), backAction != null && !backAction.isBlank());
            setStateItem(inventory, "C", messages, detail);
            if (retryAction != null && !retryAction.isBlank()) {
                setStateAction(inventory, "R", messages, retryAction);
            }
            if (backAction != null && !backAction.isBlank()) {
                setStateAction(inventory, "B", messages, backAction);
            }
            player.openInventory(inventory);
        });
    }

    public static ItemStack empty(MessageRenderer messages, String title, String detail) {
        return stateItem("N", messages, title, detail);
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
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
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
        setStateItem(inventory, "F", messages, detail);
        if (retryAction != null && !retryAction.isBlank()) {
            setStateAction(inventory, "R", messages, retryAction);
        }
        if (backAction != null && !backAction.isBlank()) {
            setStateAction(inventory, "B", messages, backAction);
        }
        player.openInventory(inventory);
    }

    private static Inventory stateInventory(UUID sessionId, MessageRenderer messages, String title, String fallback, boolean retry, boolean back) {
        Inventory inventory = sessionId == null
            ? GuiInventories.create(MENU.id(), MENU.size(), title(messages, title, fallback))
            : GuiInventories.create(MENU.id(), sessionId, MENU.size(), title(messages, title, fallback));
        GuiMenuRenderer.populate(inventory, MENU, messages, item -> (retry && item.symbol().equals("R")) || (back && item.symbol().equals("B")));
        return inventory;
    }

    private static void setStateAction(Inventory inventory, String symbol, MessageRenderer messages, String actionId) {
        GuiMenuRenderer.slots(MENU, symbol).forEach(slot -> MENU.itemAt(slot)
            .ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, java.util.Map.of(), List.of(), actionId))));
    }

    private static void setStateItem(Inventory inventory, String symbol, MessageRenderer messages, String detail) {
        GuiMenuRenderer.slots(MENU, "I").forEach(slot -> inventory.setItem(slot, stateItem(symbol, messages, null, detail)));
    }

    private static ItemStack stateItem(String symbol, MessageRenderer messages, String title, String detail) {
        GuiMenuDefinition.MenuItem item = MENU.items().get(symbol);
        if (item == null) {
            String fallbackTitle = title == null || title.isBlank() ? "State" : title;
            String fallbackDetail = detail == null || detail.isBlank() ? "" : detail;
            return GuiItems.action(GuiMenuRenderer.material("BARRIER"), fallbackTitle, "", Map.of(), fallbackDetail);
        }
        String renderedTitle = title == null || title.isBlank()
            ? GuiMenuRenderer.message(messages, item.nameKey(), item.fallbackName())
            : title;
        List<String> lore = detail == null || detail.isBlank() ? GuiMenuRenderer.lore(item, messages) : List.of(detail);
        return GuiItems.action(GuiMenuRenderer.material(item.materialKey()), renderedTitle, "", Map.of(), lore.toArray(String[]::new));
    }

    private static String title(MessageRenderer messages, String title, String fallback) {
        return title == null || title.isBlank() ? message(messages, "gui-state-title-" + fallback.toLowerCase(java.util.Locale.ROOT), fallback) : title;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
