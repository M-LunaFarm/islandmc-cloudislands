package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
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
    private static final String TITLE = "섬 권한 설정";
    private static final List<String> ROLES = List.of("MEMBER", "TRUSTED", "VISITOR", "CUSTOM_1", "CUSTOM_2", "CUSTOM_3", "CUSTOM_4", "CUSTOM_5");
    private static final List<String> PERMISSIONS = List.of("BUILD", "BREAK", "INTERACT", "OPEN_CONTAINER", "MANAGE_WARPS");
    private final MessageRenderer messages;

    public IslandPermissionMenu() {
        this(null);
    }

    public IslandPermissionMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandPermissions(islandId)
            .thenAccept(body -> openSync(plugin, player, rules(body), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "permission-menu-load-failed", "섬 권한을 불러오지 못했습니다.")));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        player.closeInventory();
        int slot = event.getRawSlot();
        if (slot == 45 || slot == 46) {
            player.performCommand("섬 권한목록");
            return;
        }
        if (slot == 47) {
            player.performCommand("섬 권한");
            return;
        }
        if (slot == 48) {
            player.performCommand("섬 설정");
            return;
        }
        if (slot == 53) {
            player.performCommand("섬 역할");
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String role = loreValue(meta, "role=");
        String permission = loreValue(meta, "permission=");
        if (role.isBlank() || permission.isBlank()) {
            return;
        }
        player.performCommand("섬 권한설정 " + role + " " + permission + " " + (!event.isRightClick()));
    }

    private static void openSync(Plugin plugin, Player player, List<Rule> rules, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            int slot = 0;
            for (String role : ROLES) {
                for (String permission : PERMISSIONS) {
                    inventory.setItem(slot++, ruleItem(role, permission, allowed(rules, role, permission), messages));
                }
            }
            inventory.setItem(45, item(Material.BOOK, message(messages, "permission-menu-all-names-name", "전체 권한 이름"), message(messages, "permission-menu-all-names-command", "/섬 권한목록"), permissionSummary()));
            inventory.setItem(46, item(Material.PAPER, message(messages, "permission-menu-list-name", "권한 목록"), message(messages, "permission-menu-list-command", "/섬 권한목록")));
            inventory.setItem(47, item(Material.CLOCK, message(messages, "permission-menu-refresh-name", "새로고침"), message(messages, "permission-menu-refresh-command", "/섬 권한")));
            inventory.setItem(48, item(Material.COMPARATOR, message(messages, "permission-menu-settings-name", "설정"), message(messages, "permission-menu-settings-command", "/섬 설정")));
            inventory.setItem(53, item(Material.NAME_TAG, message(messages, "permission-menu-role-name", "역할 설정"), message(messages, "permission-menu-role-command", "/섬 역할")));
            int summarySlot = 49;
            for (Rule rule : rules.stream().limit(5).toList()) {
                inventory.setItem(summarySlot++, ruleItem(rule.role(), rule.permission(), rule.allowed(), messages));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack ruleItem(String role, String permission, Boolean allowed, MessageRenderer messages) {
        Material material = allowed == null ? Material.GRAY_DYE : allowed ? Material.LIME_DYE : Material.RED_DYE;
        String state = allowed == null ? message(messages, "permission-menu-default", "기본값") : allowed ? message(messages, "permission-menu-allow", "허용") : message(messages, "permission-menu-deny", "차단");
        return item(material, role + " " + permission, "role=" + role, "permission=" + permission, message(messages, "permission-menu-current-state", "현재 상태: ") + state, message(messages, "permission-menu-click-actions", "좌클릭: 허용, 우클릭: 차단"));
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
