package kr.lunaf.cloudislands.paper.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PermissionRuleView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class IslandPermissionMenu implements Listener {
    private static final String TITLE_KEY = "permission-menu-title";
    private static final String TITLE = "섬 권한 설정";
    private static final String MENU_ID = "island.permissions";
    private static final List<String> ROLES = List.of("CO_OWNER", "MODERATOR", "MEMBER", "TRUSTED", "VISITOR");
    private static final int PERMISSIONS_PER_PAGE = 8;
    private static final List<String> PERMISSIONS = Arrays.stream(IslandPermission.values()).map(Enum::name).toList();
    private final MessageRenderer messages;
    private final GuiActionExecutor actions;

    public IslandPermissionMenu() {
        this(null);
    }

    public IslandPermissionMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandPermissionMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this.messages = messages;
        this.actions = actions == null ? GuiActionExecutor.noop() : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page) {
        int safePage = safePage(page);
        GuiStateMenus.openLoading(plugin, player, messages, message(messages, TITLE_KEY, TITLE));
        PaperGuiViews.islandPermissions(client, islandId)
            .thenAccept(rules -> openSync(plugin, player, rules, messages, safePage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, messages, message(messages, TITLE_KEY, TITLE), message(messages, "permission-menu-load-failed", "섬 권한을 불러오지 못했습니다."), "island.permissions.open", "island.settings.open");
                return null;
            });
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
        if (!actionId.equals("island.permissions.set")) {
            player.closeInventory();
        }
        actions.execute(player, actionId, GuiItems.data(event.getCurrentItem()), click);
    }

    private static void openSync(Plugin plugin, Player player, List<PermissionRuleView> rules, MessageRenderer messages, int page) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            int safePage = safePage(page);
            int start = safePage * PERMISSIONS_PER_PAGE;
            List<String> visiblePermissions = PERMISSIONS.subList(start, Math.min(PERMISSIONS.size(), start + PERMISSIONS_PER_PAGE));
            for (int row = 0; row < ROLES.size(); row++) {
                String role = ROLES.get(row);
                inventory.setItem(row * 9, GuiItems.action(Material.NAME_TAG, role, "island.permissions.list", message(messages, "permission-menu-role-row", "역할 권한 행")));
                for (int column = 0; column < visiblePermissions.size(); column++) {
                    String permission = visiblePermissions.get(column);
                    inventory.setItem(row * 9 + column + 1, ruleItem(role, permission, allowed(rules, role, permission), messages));
                }
            }
            inventory.setItem(45, GuiItems.action(Material.BOOK, message(messages, "permission-menu-all-names-name", "전체 권한 이름"), "island.permissions.list", message(messages, "permission-menu-matrix-policy", "전체 API 권한을 페이지로 표시합니다."), permissionSummary()));
            inventory.setItem(46, GuiItems.action(Material.ARROW, message(messages, "permission-menu-prev-page-name", "이전 페이지"), "island.permissions.page", Map.of("page", String.valueOf(Math.max(0, safePage - 1))), pageLine(safePage)));
            inventory.setItem(47, GuiItems.action(Material.ARROW, message(messages, "permission-menu-next-page-name", "다음 페이지"), "island.permissions.page", Map.of("page", String.valueOf(Math.min(maxPage(), safePage + 1))), pageLine(safePage)));
            inventory.setItem(48, GuiItems.action(Material.EMERALD_BLOCK, message(messages, "permission-menu-save-name", "변경 저장"), "island.permissions.save", message(messages, "permission-menu-save-lore", "임시 변경 사항을 한 번에 저장합니다.")));
            inventory.setItem(49, GuiItems.action(Material.CLOCK, message(messages, "permission-menu-reset-name", "변경 취소"), "island.permissions.reset", message(messages, "permission-menu-reset-lore", "임시 변경 사항을 버리고 다시 불러옵니다.")));
            inventory.setItem(50, GuiItems.action(Material.NAME_TAG, message(messages, "permission-menu-role-name", "역할 설정"), "island.roles.open"));
            inventory.setItem(51, GuiItems.action(Material.MAP, message(messages, "permission-menu-page-name", "페이지"), "island.permissions.list", pageLine(safePage)));
            inventory.setItem(52, GuiItems.action(Material.PAPER, message(messages, "permission-menu-list-name", "권한 목록"), "island.permissions.list"));
            inventory.setItem(53, GuiItems.action(Material.OAK_DOOR, message(messages, "permission-menu-settings-name", "뒤로"), "island.settings.open"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack ruleItem(String role, String permission, Boolean allowed, MessageRenderer messages) {
        Material material = allowed == null ? Material.GRAY_DYE : allowed ? Material.LIME_DYE : Material.RED_DYE;
        String state = allowed == null ? message(messages, "permission-menu-default", "기본값") : allowed ? message(messages, "permission-menu-allow", "허용") : message(messages, "permission-menu-deny", "차단");
        return GuiItems.action(material, role + " " + permissionLabel(permission), "island.permissions.set",
            Map.of("role", role, "permission", permission),
            message(messages, "permission-menu-current-state", "현재 상태: ") + state,
            message(messages, "permission-menu-matrix-cell", "Matrix: ") + role + " / " + permissionLabel(permission),
            message(messages, "permission-menu-click-actions", "좌클릭: 허용으로 임시 변경, 우클릭: 차단으로 임시 변경"));
    }

    private static String permissionLabel(String permission) {
        return switch (permission) {
            case "OPEN_CONTAINER" -> "CHEST";
            case "USE_DOOR" -> "DOOR";
            case "USE_REDSTONE" -> "REDSTONE";
            case "ATTACK_PLAYER" -> "PVP";
            case "ATTACK_MOB" -> "MOB";
            default -> permission;
        };
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static String permissionSummary() {
        StringBuilder builder = new StringBuilder();
        for (IslandPermission permission : IslandPermission.values()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(permission.name());
            if (builder.length() > 120) {
                builder.append("...");
                break;
            }
        }
        return builder.toString();
    }

    private static String pageLine(int page) {
        return "Page " + (safePage(page) + 1) + "/" + (maxPage() + 1) + " (" + PERMISSIONS.size() + " permissions)";
    }

    private static int safePage(int page) {
        return Math.max(0, Math.min(maxPage(), page));
    }

    private static int maxPage() {
        return Math.max(0, (PERMISSIONS.size() - 1) / PERMISSIONS_PER_PAGE);
    }

    private static Boolean allowed(List<PermissionRuleView> rules, String role, String permission) {
        for (PermissionRuleView rule : rules) {
            if (rule.role().equals(role) && rule.permission().equals(permission)) {
                return rule.allowed();
            }
        }
        return null;
    }

}
