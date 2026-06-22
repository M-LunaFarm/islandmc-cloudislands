package kr.lunaf.cloudislands.paper.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.PermissionRuleView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.RoleView;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
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
    private static final GuiMenuDefinition MENU = GuiMenuDefinition.bundled(
        "config-v2/ui/menus/permissions.yml",
        new GuiMenuDefinition("island.permissions", 6, TITLE_KEY, Map.of(
            "page", "island.permissions.page",
            "save", "island.permissions.save",
            "reset", "island.permissions.reset",
            "roles", "island.roles.open",
            "list", "island.permissions.list",
            "back", "island.settings.open",
            "role-prev", "island.permissions.page",
            "role-next", "island.permissions.page",
            "permission-prev", "island.permissions.page",
            "permission-next", "island.permissions.page"
        ))
    );
    private static final String MENU_ID = MENU.id();
    private static final List<String> FALLBACK_ROLES = List.of("VISITOR");
    private static final int ROLES_PER_PAGE = 5;
    private static final int PERMISSIONS_PER_PAGE = 8;
    private static final List<String> PERMISSIONS = Arrays.stream(IslandPermission.values()).map(Enum::name).toList();
    private final MessageRenderer messages;
    private final GuiActionRegistry actions;

    public IslandPermissionMenu() {
        this(null);
    }

    public IslandPermissionMenu(MessageRenderer messages) {
        this(messages, GuiActionExecutor.noop());
    }

    public IslandPermissionMenu(MessageRenderer messages, GuiActionExecutor actions) {
        this(messages, new GuiActionRegistry(actions));
    }

    public IslandPermissionMenu(MessageRenderer messages, GuiActionRegistry actions) {
        this.messages = messages;
        this.actions = actions == null ? new GuiActionRegistry(GuiActionExecutor.noop()) : actions;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        open(plugin, client, player, islandId, messages, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page) {
        open(plugin, client, player, islandId, messages, page, 0);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages, int page, int rolePage) {
        int safePage = safePage(page);
        int safeRolePage = Math.max(0, rolePage);
        GuiSession session = GuiSessions.begin(player, MENU_ID);
        GuiStateMenus.openLoading(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE));
        PaperGuiViews.islandPermissionRules(client, islandId)
            .thenCombine(PaperGuiViews.islandRoles(client, islandId), (rules, roles) -> new PermissionMenuData(rules.version(), rules.rules(), roles))
            .thenAccept(data -> openSync(plugin, player, session, data.version(), data.rules(), data.roles(), messages, safePage, safeRolePage))
            .exceptionally(error -> {
                GuiStateMenus.openError(plugin, player, session, messages, message(messages, MENU.titleKey(), TITLE), message(messages, "permission-menu-load-failed", "섬 권한을 불러오지 못했습니다."), "island.permissions.open", "island.settings.open");
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
        actions.execute(player, GuiActions.from(actionId, GuiItems.data(event.getCurrentItem())).orElse(null), click);
    }

    private static void openSync(Plugin plugin, Player player, GuiSession session, String version, List<PermissionRuleView> rules, List<RoleView> roleViews, MessageRenderer messages, int page, int rolePage) {
        GuiSessions.runIfCurrent(plugin, player, session, () -> {
            Inventory inventory = GuiMenuRenderer.render(MENU, session, messages, TITLE, item -> true);
            List<String> roles = roleNames(roleViews);
            int safePage = safePage(page);
            int safeRolePage = safeRolePage(rolePage, roles);
            int start = safePage * PERMISSIONS_PER_PAGE;
            int roleStart = safeRolePage * ROLES_PER_PAGE;
            List<String> visiblePermissions = PERMISSIONS.subList(start, Math.min(PERMISSIONS.size(), start + PERMISSIONS_PER_PAGE));
            List<String> visibleRoles = roles.subList(roleStart, Math.min(roles.size(), roleStart + ROLES_PER_PAGE));
            for (int row = 0; row < visibleRoles.size(); row++) {
                String role = visibleRoles.get(row);
                inventory.setItem(row * 9, GuiItems.action(GuiMenuRenderer.material(MENU, "ROLE", "ROLE", "NAME_TAG"), role, "island.permissions.list", message(messages, "permission-menu-role-row", "역할 권한 행")));
                for (int column = 0; column < visiblePermissions.size(); column++) {
                    String permission = visiblePermissions.get(column);
                    inventory.setItem(row * 9 + column + 1, ruleItem(role, permission, allowed(rules, role, permission), version, messages));
                }
            }
            setFooterItem(inventory, 45, messages, Map.of("page", String.valueOf(safePage), "rolePage", String.valueOf(Math.max(0, safeRolePage - 1))), rolePageLine(safeRolePage, roles));
            setFooterItem(inventory, 46, messages, Map.of("page", String.valueOf(safePage), "rolePage", String.valueOf(Math.min(maxRolePage(roles), safeRolePage + 1))), rolePageLine(safeRolePage, roles));
            setFooterItem(inventory, 47, messages, Map.of("page", String.valueOf(Math.max(0, safePage - 1)), "rolePage", String.valueOf(safeRolePage)), pageLine(safePage));
            setFooterItem(inventory, 48, messages, Map.of("page", String.valueOf(Math.min(maxPage(), safePage + 1)), "rolePage", String.valueOf(safeRolePage)), pageLine(safePage));
            setFooterItem(inventory, 52, messages, Map.of(), pageLine(safePage), rolePageLine(safeRolePage, roles), permissionSummary());
            player.openInventory(inventory);
        });
    }

    private static void setFooterItem(Inventory inventory, int slot, MessageRenderer messages, Map<String, String> data, String... extraLore) {
        MENU.itemAt(slot).ifPresent(item -> inventory.setItem(slot, GuiMenuRenderer.item(MENU, item, messages, data, List.of(extraLore))));
    }

    private static ItemStack ruleItem(String role, String permission, Boolean allowed, String version, MessageRenderer messages) {
        org.bukkit.Material material = GuiMenuRenderer.material(MENU, allowed == null ? "_" : allowed ? "ALLOW" : "DENY", "_", "GRAY_DYE");
        String state = allowed == null ? message(messages, "permission-menu-default", "기본값") : allowed ? message(messages, "permission-menu-allow", "허용") : message(messages, "permission-menu-deny", "차단");
        return GuiItems.action(material, role + " " + permissionLabel(permission), "island.permissions.set",
            Map.of("role", role, "permission", permission, "expectedVersion", version == null ? "" : version),
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
        return GuiMenuRenderer.message(messages, key, fallback);
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

    private static int safeRolePage(int rolePage, List<String> roles) {
        return Math.max(0, Math.min(maxRolePage(roles), rolePage));
    }

    private static int maxRolePage(List<String> roles) {
        return Math.max(0, (Math.max(1, roles.size()) - 1) / ROLES_PER_PAGE);
    }

    private static String rolePageLine(int rolePage, List<String> roles) {
        return "Role Page " + (safeRolePage(rolePage, roles) + 1) + "/" + (maxRolePage(roles) + 1) + " (" + roles.size() + " roles)";
    }

    private static List<String> roleNames(List<RoleView> roles) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (RoleView role : roles) {
            if (role.role() != null && !role.role().isBlank() && !role.role().equals("OWNER") && !role.role().equals("BANNED")) {
                names.add(role.role());
            }
        }
        if (names.isEmpty()) {
            names.addAll(FALLBACK_ROLES);
        } else {
            names.add("VISITOR");
        }
        return List.copyOf(names);
    }

    private static Boolean allowed(List<PermissionRuleView> rules, String role, String permission) {
        for (PermissionRuleView rule : rules) {
            if (rule.role().equals(role) && rule.permission().equals(permission)) {
                return rule.allowed();
            }
        }
        return null;
    }

    private record PermissionMenuData(String version, List<PermissionRuleView> rules, List<RoleView> roles) {}
}
