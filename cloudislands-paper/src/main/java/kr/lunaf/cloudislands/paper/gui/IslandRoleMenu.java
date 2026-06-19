package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

public final class IslandRoleMenu implements Listener {
    private static final String TITLE_KEY = "role-menu-title";
    private static final String TITLE = "섬 역할 설정";
    private final MessageRenderer messages;

    public IslandRoleMenu() {
        this(null);
    }

    public IslandRoleMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandRoles(islandId)
            .thenAccept(body -> openSync(plugin, player, roles(body), messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "role-menu-load-failed", "섬 역할을 불러오지 못했습니다.")));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!message(messages, TITLE_KEY, TITLE).equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) {
            return;
        }
        player.closeInventory();
        if (slot == 18) {
            player.performCommand("섬 역할목록");
            return;
        }
        if (slot == 19) {
            player.performCommand("섬 권한");
            return;
        }
        if (slot == 20) {
            player.performCommand("섬 역할");
            return;
        }
        if (slot == 26) {
            player.performCommand("섬 설정");
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String role = loreValue(meta, "role=");
        if (!role.isBlank()) {
            player.sendMessage(message(messages, "role-menu-edit-prefix", "역할 편집: /섬 역할편집 ") + role + message(messages, "role-menu-edit-suffix", " <weight> <displayName>"));
        }
    }

    private static void openSync(Plugin plugin, Player player, List<RoleEntry> roles, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, message(messages, TITLE_KEY, TITLE));
            int slot = 0;
            for (RoleEntry role : roles.stream().limit(18).toList()) {
                inventory.setItem(slot++, roleItem(role, messages));
            }
            if (roles.isEmpty()) {
                inventory.setItem(13, item(Material.GRAY_DYE, message(messages, "role-menu-empty-title", "커스텀 역할 없음"), message(messages, "role-menu-empty-example", "/섬 역할편집 CUSTOM_1 5 부관리자")));
            }
            inventory.setItem(18, item(Material.PAPER, message(messages, "role-menu-list-name", "역할 목록"), message(messages, "role-menu-list-command", "/섬 역할목록")));
            inventory.setItem(19, item(Material.COMPARATOR, message(messages, "role-menu-permission-name", "권한 설정"), message(messages, "role-menu-permission-command", "/섬 권한")));
            inventory.setItem(20, item(Material.CLOCK, message(messages, "role-menu-refresh-name", "새로고침"), message(messages, "role-menu-refresh-command", "/섬 역할")));
            inventory.setItem(26, item(Material.REDSTONE_TORCH, message(messages, "role-menu-settings-name", "설정"), message(messages, "role-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack roleItem(RoleEntry role, MessageRenderer messages) {
        return item(material(role.role()), role.displayName().isBlank() ? role.role() : role.displayName(),
            "role=" + role.role(),
            message(messages, "role-menu-weight", "weight=") + role.weight(),
            message(messages, "role-menu-enum", "enum=") + role.role(),
            message(messages, "role-menu-click-edit", "클릭: 편집 명령어 안내"));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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
