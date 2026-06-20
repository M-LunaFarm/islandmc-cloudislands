package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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
import org.bukkit.plugin.Plugin;

public final class IslandPermissionMenu implements Listener {
    private static final String TITLE_KEY = "permission-menu-title";
    private static final String TITLE = "섬 권한 설정";
    private static final String MENU_ID = "island.permissions";
    private static final List<String> ROLES = List.of("CO_OWNER", "MODERATOR", "MEMBER", "TRUSTED", "VISITOR");
    private static final List<String> PERMISSIONS = List.of("BUILD", "BREAK", "INTERACT", "OPEN_CONTAINER", "USE_DOOR", "USE_REDSTONE", "ATTACK_PLAYER", "ATTACK_MOB");
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
        client.listIslandPermissions(islandId)
            .thenAccept(body -> openSync(plugin, player, rules(body), messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "permission-menu-load-failed", "섬 권한을 불러오지 못했습니다.")));
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

    private static void openSync(Plugin plugin, Player player, List<Rule> rules, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = GuiInventories.create(MENU_ID, 54, message(messages, TITLE_KEY, TITLE));
            for (int row = 0; row < ROLES.size(); row++) {
                String role = ROLES.get(row);
                inventory.setItem(row * 9, GuiItems.action(Material.NAME_TAG, role, "island.permissions.list", message(messages, "permission-menu-role-row", "역할 권한 행")));
                for (int column = 0; column < PERMISSIONS.size(); column++) {
                    String permission = PERMISSIONS.get(column);
                    inventory.setItem(row * 9 + column + 1, ruleItem(role, permission, allowed(rules, role, permission), messages));
                }
            }
            inventory.setItem(45, GuiItems.action(Material.BOOK, message(messages, "permission-menu-all-names-name", "전체 권한 이름"), "island.permissions.list", message(messages, "permission-menu-matrix-policy", "표시 권한: BUILD/BREAK/INTERACT/CHEST/DOOR/REDSTONE/PVP/MOB"), permissionSummary()));
            inventory.setItem(46, GuiItems.action(Material.CLOCK, message(messages, "permission-menu-refresh-name", "새로고침"), "island.permissions.open"));
            inventory.setItem(47, GuiItems.action(Material.BARRIER, message(messages, "permission-menu-reset-name", "되돌리기"), "island.permissions.open"));
            inventory.setItem(48, GuiItems.action(Material.PAPER, message(messages, "permission-menu-list-name", "권한 목록"), "island.permissions.list"));
            inventory.setItem(49, GuiItems.action(Material.LIME_DYE, message(messages, "permission-menu-save-name", "저장"), "island.permissions.list", message(messages, "permission-menu-save-batch-note", "개별 셀 변경은 즉시 Core에 요청됩니다.")));
            inventory.setItem(50, GuiItems.action(Material.NAME_TAG, message(messages, "permission-menu-role-name", "역할 설정"), "island.roles.open"));
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
            message(messages, "permission-menu-click-actions", "좌클릭: 허용, 우클릭: 차단"));
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

    private static Boolean allowed(List<Rule> rules, String role, String permission) {
        for (Rule rule : rules) {
            if (rule.role().equals(role) && rule.permission().equals(permission)) {
                return rule.allowed();
            }
        }
        return null;
    }

    private static List<Rule> rules(String body) {
        List<Rule> rules = new ArrayList<>();
        int index = 0;
        while (body != null && index < body.length()) {
            int objectStart = body.indexOf('{', index);
            if (objectStart < 0) {
                break;
            }
            int objectEnd = body.indexOf('}', objectStart);
            if (objectEnd < 0) {
                break;
            }
            String object = body.substring(objectStart, objectEnd + 1);
            String role = text(object, "role");
            String permission = text(object, "permission");
            if (!role.isBlank() && !permission.isBlank()) {
                rules.add(new Rule(role, permission, bool(object, "allowed")));
            }
            index = objectEnd + 1;
        }
        return rules;
    }

    private static String loreValue(ItemMeta meta, String prefix) {
        if (meta.getLore() == null) {
            return "";
        }
        for (String line : meta.getLore()) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return "";
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static boolean bool(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        return start >= 0 && body.startsWith("true", start + needle.length());
    }

    private record Rule(String role, String permission, boolean allowed) {
    }
}
