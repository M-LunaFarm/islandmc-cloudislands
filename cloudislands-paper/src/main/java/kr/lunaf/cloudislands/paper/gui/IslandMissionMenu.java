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

public final class IslandMissionMenu implements Listener {
    private static final String MISSION_TITLE = "섬 미션";
    private static final String CHALLENGE_TITLE = "섬 챌린지";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, String kind) {
        client.listIslandMissions(islandId, kind)
            .thenAccept(body -> openSync(plugin, player, kind, missions(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 과제를 불러오지 못했습니다."));
                return null;
            });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!MISSION_TITLE.equals(title) && !CHALLENGE_TITLE.equals(title)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return;
        }
        String missionKey = loreValue(meta, "missionKey=");
        if (missionKey.isBlank()) {
            return;
        }
        player.closeInventory();
        player.performCommand((MISSION_TITLE.equals(title) ? "섬 미션 " : "섬 챌린지 ") + missionKey);
    }

    private static void openSync(Plugin plugin, Player player, String kind, List<Mission> missions) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, "CHALLENGE".equalsIgnoreCase(kind) ? CHALLENGE_TITLE : MISSION_TITLE);
            int slot = 0;
            for (Mission mission : missions.stream().limit(45).toList()) {
                inventory.setItem(slot++, missionItem(mission));
            }
            if (missions.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "과제 없음", "현재 표시할 섬 과제가 없습니다."));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack missionItem(Mission mission) {
        Material material = mission.completed() ? Material.LIME_DYE : Material.BOOK;
        String title = mission.title().isBlank() ? mission.key() : mission.title();
        return item(material, title, "missionKey=" + mission.key(), "진행도: " + mission.progress() + "/" + mission.goal(), "보상: " + (mission.reward().isBlank() ? "없음" : mission.reward()), mission.completed() ? "완료됨" : "클릭하면 완료를 요청합니다.");
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

    private static List<Mission> missions(String body) {
        List<Mission> missions = new ArrayList<>();
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
            String key = text(object, "missionKey");
            if (!key.isBlank()) {
                missions.add(new Mission(key, text(object, "title"), number(object, "progress"), number(object, "goal"), bool(object, "completed"), text(object, "reward")));
            }
            index = objectEnd + 1;
        }
        return missions;
    }

    private static String loreValue(ItemMeta meta, String prefix) {
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

    private static boolean bool(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        return start >= 0 && body.startsWith("true", start + needle.length());
    }

    private record Mission(String key, String title, long progress, long goal, boolean completed, String reward) {
    }
}
