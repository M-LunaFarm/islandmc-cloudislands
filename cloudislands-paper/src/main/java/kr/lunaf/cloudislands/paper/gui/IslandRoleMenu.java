package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

public final class IslandRoleMenu implements Listener {
    private static final String TITLE = "섬 역할 설정";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandRoles(islandId)
            .thenAccept(body -> openSync(plugin, player, roles(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 역할을 불러오지 못했습니다."));
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
        if (meta == null || !meta.hasDisplayName()) {
            return;
        }
        String name = meta.getDisplayName();
        player.closeInventory();
        if (name.equals("역할 목록")) {
            player.performCommand("섬 역할목록");
            return;
        }
        if (name.equals("권한 설정")) {
            player.performCommand("섬 권한");
            return;
        }
        if (name.equals("설정")) {
            player.performCommand("섬 설정");
            return;
        }
        if (name.equals("새로고침")) {
            player.performCommand("섬 역할");
            return;
        }
        String role = loreValue(meta, "role=");
        if (!role.isBlank()) {
            player.sendMessage("역할 편집: /섬 역할편집 " + role + " <weight> <displayName>");
        }
    }

    private static void openSync(Plugin plugin, Player player, List<RoleEntry> roles) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
            int slot = 0;
            for (RoleEntry role : roles.stream().limit(18).toList()) {
                inventory.setItem(slot++, roleItem(role));
            }
            if (roles.isEmpty()) {
                inventory.setItem(13, item(Material.GRAY_DYE, "커스텀 역할 없음", "/섬 역할편집 CUSTOM_1 5 부관리자"));
            }
            inventory.setItem(18, item(Material.PAPER, "역할 목록", "/섬 역할목록"));
            inventory.setItem(19, item(Material.COMPARATOR, "권한 설정", "/섬 권한"));
            inventory.setItem(20, item(Material.CLOCK, "새로고침", "/섬 역할"));
            inventory.setItem(26, item(Material.REDSTONE_TORCH, "설정", "/섬 설정"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack roleItem(RoleEntry role) {
        return item(material(role.role()), role.displayName().isBlank() ? role.role() : role.displayName(),
            "role=" + role.role(),
            "weight=" + role.weight(),
            "enum=" + role.role(),
            "클릭: 편집 명령어 안내");
    }

    private static Material material(String role) {
        if (role.startsWith("CUSTOM_")) {
            return Material.NAME_TAG;
        }
        return switch (role) {
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            case "MEMBER" -> Material.PLAYER_HEAD;
            default -> Material.PAPER;
        };
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

    private static List<RoleEntry> roles(String body) {
        List<RoleEntry> roles = new ArrayList<>();
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
            if (!role.isBlank()) {
                roles.add(new RoleEntry(role, integer(object, "weight"), text(object, "displayName")));
            }
            index = objectEnd + 1;
        }
        return roles;
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

    private static int integer(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        if (start < 0) {
            return 0;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && (Character.isDigit(body.charAt(end)) || body.charAt(end) == '-')) {
            end++;
        }
        try {
            return Integer.parseInt(body.substring(start, end));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private record RoleEntry(String role, int weight, String displayName) {
    }
}
