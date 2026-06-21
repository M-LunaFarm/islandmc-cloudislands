package kr.lunaf.cloudislands.paper.gui;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class IslandMainMenu implements Listener {
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/main.yml",
        new GuiMenuDefinition("island.main", 3, "main-menu-title", java.util.Map.of())
    );
    private static final String TITLE = "섬 메뉴";
    private static final String MENU_ID = MENU.id();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandMainMenu() {
        this(null);
    }

    public IslandMainMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandMainMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this(messages, new GuiActionRegistry(actions));
    }

    public IslandMainMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(MENU, messages, TITLE, item -> showItem(player, item));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiInventories.isMenu(event.getView().getTopInventory(), MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        GuiClick click = GuiClick.from(event);
        if (!click.supported()) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        if (actionId.equals("island.visit.open") && click.right()) {
            actions.execute(player, "island.visit.random", GuiItems.data(event.getCurrentItem()), click);
            return;
        }
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static boolean showItem(Player player, GuiMenuDefinition.MenuItem item) {
        if (!item.actionKey().equals("admin.node.open")) {
            return true;
        }
        return player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.node");
    }

}
