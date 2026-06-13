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

public final class IslandHomeMenu implements Listener {
    private static final String TITLE_KEY = "home-menu-title";
    private static final String TITLE = "섬 홈 관리";
    private final MessageRenderer messages;

    public IslandHomeMenu() {
        this(null);
    }

    public IslandHomeMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandHomes(islandId)
            .thenAccept(body -> openSync(plugin, player, homes(body), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "home-menu-load-failed", "섬 홈을 불러오지 못했습니다.")));
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
        if (slot == 45) {
            if (event.isRightClick()) {
                player.sendMessage(message(messages, "home-menu-set-usage", "사용법: /섬 셋홈 <이름>"));
                return;
            }
            player.performCommand("섬 셋홈 default");
            return;
        }
        if (slot == 49) {
            player.performCommand("섬 설정");
            return;
        }
        if (slot == 53) {
            player.performCommand("섬 메뉴");
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String homeName = loreValue(meta, "homeName=");
        if (!homeName.isBlank()) {
            if (event.isRightClick()) {
                player.performCommand("섬 셋홈 " + homeName);
                return;
            }
            player.performCommand("섬 home " + homeName);
        }
    }

    private static void openSync(Plugin plugin, Player player, List<Home> homes, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, message(messages, TITLE_KEY, TITLE));
            inventory.setItem(45, item(Material.RED_BED, message(messages, "home-menu-set-current-name", "현재 위치를 홈으로 설정"), message(messages, "home-menu-set-default-click", "좌클릭: default 홈으로 설정"), message(messages, "home-menu-set-named-click", "우클릭: ") + message(messages, "home-menu-set-usage", "사용법: /섬 셋홈 <이름>")));
            int slot = 0;
            for (Home home : homes.stream().limit(45).toList()) {
                inventory.setItem(slot++, homeItem(home, messages));
            }
            if (homes.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "home-menu-empty-title", "홈 없음"), message(messages, "home-menu-empty", "현재 등록된 섬 홈이 없습니다.")));
            }
            inventory.setItem(49, item(Material.COMPARATOR, message(messages, "home-menu-settings-name", "설정"), message(messages, "home-menu-settings-command", "/섬 설정")));
            inventory.setItem(53, item(Material.COMPASS, message(messages, "home-menu-main-menu-name", "메인 메뉴"), message(messages, "home-menu-main-menu-command", "/섬 메뉴")));
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

    private static ItemStack homeItem(Home home, MessageRenderer messages) {
        return item(Material.GREEN_BED, home.name(), "homeName=" + home.name(), message(messages, "home-menu-location", "위치: ") + (long) home.x() + ", " + (long) home.y() + ", " + (long) home.z(), home.createdAt().isBlank() ? message(messages, "home-menu-no-created-info", "생성 정보 없음") : message(messages, "home-menu-created-at", "생성 시각: ") + home.createdAt(), message(messages, "home-menu-left-click", "좌클릭: 이 홈으로 이동"), message(messages, "home-menu-right-click", "우클릭: 현재 위치로 갱신"));
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

    private static List<Home> homes(String body) {
        List<Home> homes = new ArrayList<>();
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
            String name = text(object, "name");
            if (!name.isBlank()) {
                homes.add(new Home(name, decimal(object, "localX"), decimal(object, "localY"), decimal(object, "localZ"), text(object, "createdAt")));
            }
            index = objectEnd + 1;
        }
        return homes;
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

    private static double decimal(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        if (start < 0) {
            return 0.0D;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && "-0123456789.".indexOf(body.charAt(end)) >= 0) {
            end++;
        }
        try {
            return Double.parseDouble(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0.0D;
        }
    }

    private record Home(String name, double x, double y, double z, String createdAt) {
    }
}
