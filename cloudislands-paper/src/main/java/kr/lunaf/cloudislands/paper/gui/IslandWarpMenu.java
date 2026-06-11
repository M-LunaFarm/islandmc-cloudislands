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

public final class IslandWarpMenu implements Listener {
    private static final String TITLE = "섬 워프 관리";
    private static final String PUBLIC_TITLE = "공개 섬 워프";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandWarps(islandId)
            .thenAccept(body -> openSync(plugin, player, TITLE, warps(body), false))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 워프를 불러오지 못했습니다."));
                return null;
            });
    }

    public static void openPublic(Plugin plugin, CoreApiClient client, Player player) {
        client.listPublicWarps(45)
            .thenAccept(body -> openSync(plugin, player, PUBLIC_TITLE, warps(body), true))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("공개 섬 워프를 불러오지 못했습니다."));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        boolean publicMenu = PUBLIC_TITLE.equals(event.getView().getTitle());
        if (!TITLE.equals(event.getView().getTitle()) && !publicMenu) {
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
        if (name.equals("현재 위치를 워프로 설정")) {
            player.sendMessage("사용법: /섬 워프설정 <이름>");
            return;
        }
        if (name.equals("공개 워프 새로고침")) {
            player.performCommand("섬 공개워프목록");
            return;
        }
        String warpName = loreValue(meta, "warpName=");
        if (warpName.isBlank()) {
            return;
        }
        String islandId = loreValue(meta, "islandId=");
        if (publicMenu && !islandId.isBlank()) {
            player.performCommand("섬 warp " + islandId + " " + warpName);
            return;
        }
        if (event.isRightClick()) {
            player.sendMessage("워프 관리: /섬 워프공개 " + warpName);
            player.sendMessage("워프 관리: /섬 워프비공개 " + warpName);
            player.sendMessage("워프 관리: /섬 워프삭제 " + warpName);
            return;
        }
        player.performCommand("섬 warp " + warpName);
    }

    private static void openSync(Plugin plugin, Player player, String title, List<Warp> warps, boolean publicMenu) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, title);
            inventory.setItem(45, publicMenu
                ? item(Material.COMPASS, "공개 워프 새로고침", "/섬 공개워프목록")
                : item(Material.ENDER_PEARL, "현재 위치를 워프로 설정", "사용법: /섬 워프설정 <이름>"));
            int slot = 0;
            for (Warp warp : warps.stream().limit(45).toList()) {
                inventory.setItem(slot++, warpItem(warp, publicMenu));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack warpItem(Warp warp, boolean publicMenu) {
        Material material = warp.publicAccess() ? Material.ENDER_EYE : Material.ENDER_PEARL;
        if (publicMenu) {
            return item(material, warp.name(), "islandId=" + warp.islandId(), "warpName=" + warp.name(), "위치: " + (long) warp.x() + ", " + (long) warp.y() + ", " + (long) warp.z(), "좌클릭: 공개 워프로 이동");
        }
        return item(material, warp.name(), "warpName=" + warp.name(), "위치: " + (long) warp.x() + ", " + (long) warp.y() + ", " + (long) warp.z(), warp.publicAccess() ? "공개 워프" : "비공개 워프", "좌클릭: 이동, 우클릭: 관리 명령");
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

    private static List<Warp> warps(String body) {
        List<Warp> warps = new ArrayList<>();
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
                warps.add(new Warp(text(object, "islandId"), name, decimal(object, "localX"), decimal(object, "localY"), decimal(object, "localZ"), bool(object, "publicAccess")));
            }
            index = objectEnd + 1;
        }
        return warps;
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
        int end = jsonStringEnd(body, start);
        if (end < start) {
            return "";
        }
        return body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
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

    private static boolean bool(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        return start >= 0 && body.startsWith("true", start + needle.length());
    }

    private static int jsonStringEnd(String body, int start) {
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' && !escaped) {
                return i;
            }
            escaped = c == '\\' && !escaped;
            if (c != '\\') {
                escaped = false;
            }
        }
        return -1;
    }

    private record Warp(String islandId, String name, double x, double y, double z, boolean publicAccess) {
    }
}
