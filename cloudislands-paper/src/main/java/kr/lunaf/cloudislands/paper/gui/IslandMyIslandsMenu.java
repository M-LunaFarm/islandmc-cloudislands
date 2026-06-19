package kr.lunaf.cloudislands.paper.gui;

import java.util.ArrayList;
import java.util.List;
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

public final class IslandMyIslandsMenu implements Listener {
    private static final String TITLE_KEY = "my-islands-menu-title";
    private static final String TITLE = "내 섬 목록";
    private final MessageRenderer messages;

    public IslandMyIslandsMenu() {
        this(null);
    }

    public IslandMyIslandsMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player) {
        open(plugin, client, player, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, MessageRenderer messages) {
        client.listPlayerIslands(player.getUniqueId())
            .thenAccept(body -> openSync(plugin, player, islands(body), messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "my-islands-menu-load-failed", "내 섬 목록을 불러오지 못했습니다.")));
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
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 49) {
            player.performCommand("섬 목록");
            return;
        }
        if (slot == 48) {
            player.performCommand("섬 생성메뉴");
            return;
        }
        if (slot == 45) {
            player.performCommand("섬 메뉴");
            return;
        }
        if (slot == 53) {
            player.performCommand("섬 방문");
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String islandId = loreValue(meta, "섬 ID=");
        if (!islandId.isBlank()) {
            player.performCommand("섬 방문 " + islandId);
        }
    }

    private static void openSync(Plugin plugin, Player player, List<IslandEntry> islands, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, message(messages, TITLE_KEY, TITLE));
            if (islands.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "my-islands-menu-empty-title", "섬 없음"), message(messages, "my-islands-menu-empty", "속한 섬이 없습니다.")));
            } else {
                for (int index = 0; index < islands.size() && index < 45; index++) {
                    IslandEntry island = islands.get(index);
                    inventory.setItem(index, item(material(island.role()), island.name(), "섬 ID=" + island.islandId(), message(messages, "my-islands-menu-role", "역할: ") + island.role(), message(messages, "my-islands-menu-state", "상태: ") + island.state(), message(messages, "my-islands-menu-level", "레벨: ") + island.level(), message(messages, "my-islands-menu-worth", "가치: ") + island.worth(), message(messages, "my-islands-menu-click-to-visit", "클릭하면 이 섬으로 이동합니다.")));
                }
            }
            inventory.setItem(45, item(Material.COMPASS, message(messages, "my-islands-menu-main-menu-name", "메인 메뉴"), message(messages, "my-islands-menu-main-menu-command", "/섬 메뉴")));
            inventory.setItem(48, item(Material.OAK_SAPLING, message(messages, "my-islands-menu-create-name", "섬 생성"), message(messages, "my-islands-menu-create-command", "/섬 생성메뉴")));
            inventory.setItem(49, item(Material.CLOCK, message(messages, "my-islands-menu-refresh-name", "새로고침"), message(messages, "my-islands-menu-refresh-command", "/섬 목록")));
            inventory.setItem(53, item(Material.ENDER_PEARL, message(messages, "my-islands-menu-public-islands-name", "공개 섬"), message(messages, "my-islands-menu-public-islands-command", "/섬 방문")));
            player.openInventory(inventory);
        });
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    private static Material material(String role) {
        return switch (role) {
            case "OWNER" -> Material.NETHER_STAR;
            case "CO_OWNER" -> Material.DIAMOND;
            case "MODERATOR" -> Material.IRON_SWORD;
            case "TRUSTED" -> Material.EMERALD;
            default -> Material.GRASS_BLOCK;
        };
    }

    private static List<IslandEntry> islands(String body) {
        List<IslandEntry> islands = new ArrayList<>();
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
            String islandId = text(object, "islandId");
            if (!islandId.isBlank()) {
                String name = text(object, "name");
                islands.add(new IslandEntry(islandId, name.isBlank() ? islandId : name, text(object, "state"), role(object), number(object, "level"), text(object, "worth")));
            }
            index = objectEnd + 1;
        }
        return islands;
    }

    private static String role(String object) {
        String role = text(object, "role");
        return role.isBlank() ? "MEMBER" : role;
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

    private static long number(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        if (start < 0) {
            return 0L;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private record IslandEntry(String islandId, String name, String state, String role, long level, String worth) {
    }
}
