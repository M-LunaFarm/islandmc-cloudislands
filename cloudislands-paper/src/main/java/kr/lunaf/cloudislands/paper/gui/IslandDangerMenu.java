package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class IslandDangerMenu implements Listener {
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/danger.yml",
        new GuiMenuDefinition("island.danger", 3, "danger-menu-title", java.util.Map.of(
            "snapshot", "island.snapshots.open",
            "reset", "island.danger.reset.prepare",
            "delete", "island.danger.delete.prepare",
            "back", "island.settings.open"
        ))
    );
    private static final GuiMenuDefinition RESET_CONFIRM_MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/danger-reset-confirm.yml",
        new GuiMenuDefinition("island.danger.reset-confirm", 3, "menu.danger.reset-confirm.title", java.util.Map.of(
            "cancel", "island.danger.open",
            "confirm", "island.danger.reset.confirm"
        ))
    );
    private static final GuiMenuDefinition DELETE_CONFIRM_MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/danger-delete-confirm.yml",
        new GuiMenuDefinition("island.danger.delete-confirm", 3, "menu.danger.delete-confirm.title", java.util.Map.of(
            "cancel", "island.danger.open",
            "confirm", "island.danger.delete.confirm"
        ))
    );
    private static final String TITLE = "섬 위험 작업";
    private static final String MENU_ID = MENU.id();
    private static final String RESET_CONFIRM_MENU_ID = RESET_CONFIRM_MENU.id();
    private static final String DELETE_CONFIRM_MENU_ID = DELETE_CONFIRM_MENU.id();
    private static final String RESET_CONFIRM_TITLE = "섬 리셋 확인";
    private static final String DELETE_CONFIRM_TITLE = "섬 삭제 확인";
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandDangerMenu() {
        this(null);
    }

    public IslandDangerMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandDangerMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this(messages, new GuiActionRegistry(actions));
    }

    public IslandDangerMenu(MessageRenderer messages, GuiActionRegistry actions) {
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

    public static void openResetConfirm(Player player, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(RESET_CONFIRM_MENU, messages, RESET_CONFIRM_TITLE);
        attachConfirmData(inventory, RESET_CONFIRM_MENU, messages, DangerousGuiActionPolicy.resetConfirmationData());
        player.openInventory(inventory);
    }

    public static void openDeleteConfirm(Player player, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(DELETE_CONFIRM_MENU, messages, DELETE_CONFIRM_TITLE);
        attachConfirmData(inventory, DELETE_CONFIRM_MENU, messages, DangerousGuiActionPolicy.deleteConfirmationData());
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!GuiInventories.isMenu(event.getView().getTopInventory(), MENU_ID)
            && !GuiInventories.isMenu(event.getView().getTopInventory(), RESET_CONFIRM_MENU_ID)
            && !GuiInventories.isMenu(event.getView().getTopInventory(), DELETE_CONFIRM_MENU_ID)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null || !GuiItems.topInventoryClick(event)) {
            return;
        }
        GuiClick click = GuiClick.from(event);
        if (click != GuiClick.LEFT && click != GuiClick.RIGHT) {
            return;
        }
        String actionId = GuiItems.actionId(event.getCurrentItem());
        if (actionId.isBlank()) {
            return;
        }
        player.closeInventory();
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    private static void attachConfirmData(Inventory inventory, GuiMenuDefinition menu, MessageRenderer messages, Map<String, String> data) {
        GuiMenuRenderer.actionSlots(menu, "confirm").stream().findFirst()
            .ifPresent(slot -> menu.itemAt(slot)
                .ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(menu, item, messages, data))));
    }
}
