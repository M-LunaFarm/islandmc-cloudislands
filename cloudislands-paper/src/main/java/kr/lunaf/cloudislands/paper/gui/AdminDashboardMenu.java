package kr.lunaf.cloudislands.paper.gui;

import java.util.Map;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public final class AdminDashboardMenu implements Listener {
    private static final String TITLE = "CloudIslands 운영 대시보드";
    private static final Map<String, String> ACTION_PERMISSIONS = Map.ofEntries(
        Map.entry("admin.node.list", "cloudislands.admin.node"),
        Map.entry("admin.jobs.open", "cloudislands.admin.jobs"),
        Map.entry("admin.route.open", "cloudislands.admin.route"),
        Map.entry("admin.storage.open", "cloudislands.admin.storage"),
        Map.entry("admin.events.open", "cloudislands.admin.events"),
        Map.entry("admin.audit.open", "cloudislands.admin.audit"),
        Map.entry("admin.metrics.open", "cloudislands.admin.metrics"),
        Map.entry("admin.reviews.open", "cloudislands.admin.island"),
        Map.entry("admin.templates.open", "cloudislands.admin.templates"),
        Map.entry("admin.migration.open", "cloudislands.admin.migrate-superiorskyblock2")
    );
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/admin-dashboard.yml",
        new GuiMenuDefinition("admin.dashboard", 5, "admin-dashboard-menu-title", Map.ofEntries(
            Map.entry("nodes", "admin.node.list"),
            Map.entry("jobs", "admin.jobs.open"),
            Map.entry("routes", "admin.route.open"),
            Map.entry("storage", "admin.storage.open"),
            Map.entry("events", "admin.events.open"),
            Map.entry("audit", "admin.audit.open"),
            Map.entry("metrics", "admin.metrics.open"),
            Map.entry("reviews", "admin.reviews.open"),
            Map.entry("templates", "admin.templates.open"),
            Map.entry("migration", "admin.migration.open"),
            Map.entry("main", "island.main.open"),
            Map.entry("close", "gui.close")
        ))
    );
    private static final String MENU_ID = MENU.id();
    private final GuiActionRegistry actions;

    public AdminDashboardMenu() {
        this(null);
    }

    public AdminDashboardMenu(MessageRenderer messages) {
        this(messages, new GuiActionRegistry(GuiActionExecutor.noop()));
    }

    public AdminDashboardMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Player player, MessageRenderer messages) {
        Inventory inventory = GuiMenuRenderer.render(MENU, messages, TITLE, item -> true);
        applyPermissionLocks(player, inventory, messages);
        player.openInventory(inventory);
    }

    public static boolean canOpen(Player player) {
        if (player == null) {
            return false;
        }
        if (player.hasPermission("cloudislands.admin") || player.hasPermission("cloudislands.admin.dashboard")) {
            return true;
        }
        return ACTION_PERMISSIONS.values().stream().anyMatch(player::hasPermission);
    }

    static String requiredPermission(String actionId) {
        return ACTION_PERMISSIONS.getOrDefault(actionId == null ? "" : actionId, "");
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
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
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
        if (actionId.equals("gui.close")) {
            return;
        }
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    private static void applyPermissionLocks(Player player, Inventory inventory, MessageRenderer messages) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int currentSlot = slot;
            MENU.itemAt(slot)
                .filter(item -> !permitted(player, MENU.action(item.actionKey(), item.actionKey())))
                .ifPresent(item -> inventory.setItem(currentSlot, lockedItem(item, messages)));
        }
    }

    private static boolean permitted(Player player, String actionId) {
        String permission = requiredPermission(actionId);
        return permission.isBlank()
            || player.hasPermission("cloudislands.admin")
            || player.hasPermission(permission);
    }

    private static org.bukkit.inventory.ItemStack lockedItem(GuiMenuDefinition.MenuItem item, MessageRenderer messages) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>(GuiMenuRenderer.lore(item, messages));
        lore.add(GuiMenuRenderer.message(messages, "gui-button-state-disabled-no-permission", "상태: 권한 없음"));
        return GuiItems.action(
            Material.BARRIER,
            GuiMenuRenderer.message(messages, item.nameKey(), item.fallbackName()),
            "",
            lore.toArray(String[]::new)
        );
    }
}
