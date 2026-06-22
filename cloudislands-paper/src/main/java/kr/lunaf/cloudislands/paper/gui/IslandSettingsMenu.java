package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.IslandInfoView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

public final class IslandSettingsMenu implements Listener {
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/settings.yml",
        new GuiMenuDefinition("island.settings", 3, "settings-menu-title", java.util.Map.of(
            "public-toggle", "island.public.toggle",
            "lock-toggle", "island.lock.toggle",
            "back", "island.main.open"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final String TITLE = "섬 설정";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandSettingsMenu() {
        this(null);
    }

    public IslandSettingsMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public IslandSettingsMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, java.util.UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, java.util.UUID islandId, MessageRenderer messages) {
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandInfo(client, islandId)
            .thenAccept(view -> openSync(plugin, player, session, view, messages))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "settings-menu-load-failed", "섬 설정을 불러오지 못했습니다."), "island.settings.open", "island.main.open");
                return null;
            });
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, IslandInfoView view, MessageRenderer messages) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            boolean publicAccess = view.publicAccess();
            boolean locked = view.locked();
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            setStateItem(inventory, "P", messages, publicAccess,
                message(messages, "settings-menu-current", "현재: ") + (publicAccess ? message(messages, "settings-menu-public", "공개") : message(messages, "settings-menu-private", "비공개")));
            setStateItem(inventory, "L", messages, locked,
                message(messages, "settings-menu-current", "현재: ") + (locked ? message(messages, "settings-menu-locked", "잠김") : message(messages, "settings-menu-open", "열림")));
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }

    private static void setStateItem(Inventory inventory, String symbol, MessageRenderer messages, boolean active, String currentLore) {
        GuiMenuRenderer.slots(MENU, symbol).forEach(slot -> MENU.itemAt(slot)
            .ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.stateItem(MENU, item, messages, active, java.util.Map.of(), List.of(currentLore)))));
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
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }

}
