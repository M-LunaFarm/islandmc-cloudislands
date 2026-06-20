package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class IslandDangerMenu implements Listener {
    private static final String TITLE_KEY = "danger-menu-title";
    private static final String TITLE = "섬 위험 작업";
    private static final String MENU_ID = "island.danger";
    private static final String RESET_CONFIRM_MENU_ID = "island.danger.reset-confirm";
    private static final String DELETE_CONFIRM_MENU_ID = "island.danger.delete-confirm";
    private static final String RESET_CONFIRM_TITLE = "섬 리셋 확인";
    private static final String DELETE_CONFIRM_TITLE = "섬 삭제 확인";
    private final MessageRenderer messages;
    private final GuiActionExecutor actions;

    public IslandDangerMenu() {
        this(null);
    }

    public IslandDangerMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandDangerMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this.messages = messages;
        this.actions = actions == null ? GuiActionExecutor.noop() : actions;
    }

    public static void open(Player player) {
        open(player, null);
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = GuiInventories.create(MENU_ID, 27, message(messages, TITLE_KEY, TITLE));
        inventory.setItem(10, GuiItems.action(Material.CHEST, message(messages, "danger-menu-snapshot-name", "스냅샷 확인"), "island.snapshots.open", message(messages, "danger-menu-snapshot-description", "위험 작업 전에 복구 지점을 확인합니다.")));
        inventory.setItem(12, GuiItems.action(Material.TNT, message(messages, "danger-menu-reset-name", "섬 리셋"), "island.danger.reset.prepare", message(messages, "danger-menu-reset-description", "월드 초기화 범위를 확인한 뒤 실행합니다.")));
        inventory.setItem(14, GuiItems.action(Material.LAVA_BUCKET, message(messages, "danger-menu-delete-name", "섬 삭제"), "island.danger.delete.prepare", message(messages, "danger-menu-delete-description", "삭제 영향을 확인한 뒤 요청합니다.")));
        inventory.setItem(22, GuiItems.action(Material.OAK_DOOR, message(messages, "danger-menu-back-name", "돌아가기"), "island.settings.open"));
        player.openInventory(inventory);
    }

    public static void openResetConfirm(Player player, MessageRenderer messages) {
        Inventory inventory = GuiInventories.create(RESET_CONFIRM_MENU_ID, 27, RESET_CONFIRM_TITLE);
        inventory.setItem(11, GuiItems.action(Material.OAK_DOOR, message(messages, "danger-confirm-cancel-name", "취소"), "island.danger.open"));
        inventory.setItem(15, GuiItems.action(Material.TNT, message(messages, "danger-reset-confirm-name", "리셋 실행"), "island.danger.reset.confirm",
            message(messages, "danger-reset-confirm-line-1", "월드 블록과 엔티티가 초기화됩니다."),
            message(messages, "danger-reset-confirm-line-2", "멤버·은행·권한은 유지됩니다.")));
        player.openInventory(inventory);
    }

    public static void openDeleteConfirm(Player player, MessageRenderer messages) {
        Inventory inventory = GuiInventories.create(DELETE_CONFIRM_MENU_ID, 27, DELETE_CONFIRM_TITLE);
        inventory.setItem(11, GuiItems.action(Material.OAK_DOOR, message(messages, "danger-confirm-cancel-name", "취소"), "island.danger.open"));
        inventory.setItem(15, GuiItems.action(Material.LAVA_BUCKET, message(messages, "danger-delete-confirm-name", "삭제 요청"), "island.danger.delete.confirm",
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
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }
}
