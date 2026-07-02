package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class AdminJobMenu implements Listener {
    private static final String TITLE_KEY = "admin-job-menu-title";
    private static final String TITLE = "섬 작업 관리";
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-jobs.yml",
        new GuiMenuDefinition("admin.jobs", 3, TITLE_KEY, Map.ofEntries(
            Map.entry("list", "admin.jobs.list"),
            Map.entry("retry", "admin.jobs.retry.prompt"),
            Map.entry("cancel", "admin.jobs.cancel.prompt"),
            Map.entry("close", "gui.close")
        ))
    );
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public AdminJobMenu() {
        this(null);
    }

    public AdminJobMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminJobMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(MENU, messages, TITLE, item -> true);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiItems.menuClick(event, MENU.id())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.equals("gui.close")) {
            player.closeInventory();
            return;
        }
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), GuiClick.from(event));
    }
}
