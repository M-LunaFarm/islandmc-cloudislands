package kr.lunaf.cloudislands.paper.gui;

import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
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
    private static final String TITLE = "섬 위험 작업";
    private static final String MENU_ID = MENU.id();
    private static final String RESET_CONFIRM_MENU_ID = "island.danger.reset-confirm";
    private static final String DELETE_CONFIRM_MENU_ID = "island.danger.delete-confirm";
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
        Inventory inventory = GuiInventories.create(RESET_CONFIRM_MENU_ID, 27, RESET_CONFIRM_TITLE);
        inventory.setItem(11, GuiItems.action(Material.OAK_DOOR, message(messages, "danger-confirm-cancel-name", "취소"), "island.danger.open"));
        inventory.setItem(15, GuiItems.action(Material.TNT, message(messages, "danger-reset-confirm-name", "리셋 실행"), "island.danger.reset.confirm", DangerousGuiActionPolicy.resetConfirmationData(),
            message(messages, "danger-reset-confirm-line-1", "월드 블록과 엔티티가 초기화됩니다."),
            message(messages, "danger-reset-confirm-line-2", "멤버·은행·권한은 유지됩니다.")));
        player.openInventory(inventory);
    }

    public static void openDeleteConfirm(Player player, MessageRenderer messages) {
        Inventory inventory = GuiInventories.create(DELETE_CONFIRM_MENU_ID, 27, DELETE_CONFIRM_TITLE);
        inventory.setItem(11, GuiItems.action(Material.OAK_DOOR, message(messages, "danger-confirm-cancel-name", "취소"), "island.danger.open"));
        inventory.setItem(15, GuiItems.action(Material.LAVA_BUCKET, message(messages, "danger-delete-confirm-name", "삭제 요청"), "island.danger.delete.confirm", DangerousGuiActionPolicy.deleteConfirmationData(),
            message(messages, "danger-delete-confirm-line-1", "섬을 삭제 요청 상태로 전환합니다."),
            message(messages, "danger-delete-confirm-line-2", "복구 유예와 감사 로그는 Core 정책을 따릅니다.")));
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
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        return GuiMenuRenderer.message(messages, key, fallback);
    }
}
