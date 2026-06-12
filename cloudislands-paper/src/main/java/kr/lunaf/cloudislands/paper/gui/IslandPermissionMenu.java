package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
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
    private static final List<String> ROLES = List.of("MEMBER", "TRUSTED", "VISITOR");
    private static final List<String> PERMISSIONS = List.of("BUILD", "BREAK", "INTERACT", "OPEN_CONTAINER", "MANAGE_WARPS", "SET_HOME", "SET_BIOME");

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandPermissions(islandId)
            .thenAccept(body -> openSync(plugin, player, rules(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 권한을 불러오지 못했습니다."));
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
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String displayName = meta.getDisplayName();
        player.closeInventory();
        if ("권한 목록".equals(displayName)) {
            player.performCommand("섬 권한목록");
            return;
        }
        if ("새로고침".equals(displayName)) {
            player.performCommand("섬 권한");
            return;
        }
        if ("설정".equals(displayName)) {
            player.performCommand("섬 설정");
            return;
        }
        String role = loreValue(meta, "role=");
        String permission = loreValue(meta, "permission=");
        if (role.isBlank() || permission.isBlank()) {
            return;
        }
        player.performCommand("섬 권한설정 " + role + " " + permission + " " + (!event.isRightClick()));
    }

    private static void openSync(Plugin plugin, Player player, List<Rule> rules) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            int slot = 0;
            for (String role : ROLES) {
                for (String permission : PERMISSIONS) {
                    inventory.setItem(slot++, ruleItem(role, permission, allowed(rules, role, permission)));
                }
                slot += 2;
            }
            inventory.setItem(30, item(Material.BOOK, "전체 권한 이름", "/섬 권한목록", permissionSummary()));
            inventory.setItem(31, item(Material.PAPER, "권한 목록", "/섬 권한목록"));
            inventory.setItem(32, item(Material.CLOCK, "새로고침", "/섬 권한"));
            inventory.setItem(33, item(Material.COMPARATOR, "설정", "/섬 설정"));
            int summarySlot = 36;
            for (Rule rule : rules.stream().limit(18).toList()) {
                inventory.setItem(summarySlot++, ruleItem(rule.role(), rule.permission(), rule.allowed()));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack ruleItem(String role, String permission, Boolean allowed) {
        Material material = allowed == null ? Material.GRAY_DYE : allowed ? Material.LIME_DYE : Material.RED_DYE;
        String state = allowed == null ? "기본값" : allowed ? "허용" : "차단";
        return item(material, role + " " + permission, "role=" + role, "permission=" + permission, "현재 상태: " + state, "좌클릭: 허용, 우클릭: 차단");
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
