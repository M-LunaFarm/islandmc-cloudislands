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

public final class IslandBanMenu implements Listener {
    private static final String TITLE = "방문자 밴 목록";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandBans(islandId)
            .thenAccept(body -> openSync(plugin, player, bans(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 밴 목록을 불러오지 못했습니다."));
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
        if (name.equals("새로고침")) {
            player.performCommand("섬 밴목록");
            return;
        }
        String bannedUuid = loreValue(meta, "bannedUuid=");
        if (bannedUuid.isBlank()) {
            return;
        }
        if (event.isRightClick()) {
            player.performCommand("섬 밴해제 " + bannedUuid);
            return;
        }
        player.sendMessage("방문자 밴 상세");
        if (meta.getLore() != null) {
            for (String line : meta.getLore()) {
                player.sendMessage("- " + line);
            }
        }
    }

    private static void openSync(Plugin plugin, Player player, List<Ban> bans) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            if (bans.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "밴 기록 없음", "현재 밴된 방문자가 없습니다."));
            } else {
                for (int index = 0; index < bans.size() && index < 45; index++) {
                    inventory.setItem(index, banItem(bans.get(index)));
                }
            }
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 밴목록"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack banItem(Ban ban) {
        return item(Material.BARRIER, "밴 " + shortUuid(ban.bannedUuid()), "bannedUuid=" + ban.bannedUuid(), "actorUuid=" + ban.actorUuid(), "reason=" + ban.reason(), "createdAt=" + ban.createdAt(), "expiresAt=" + (ban.expiresAt().isBlank() ? "none" : ban.expiresAt()), "좌클릭: 상세 보기", "우클릭: 밴 해제");
    }

    private static List<Ban> bans(String body) {
        List<Ban> bans = new ArrayList<>();
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
            String bannedUuid = text(object, "bannedUuid");
            if (!bannedUuid.isBlank()) {
                bans.add(new Ban(bannedUuid, text(object, "actorUuid"), text(object, "reason"), text(object, "createdAt"), text(object, "expiresAt")));
            }
            index = objectEnd + 1;
        }
        return bans;
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

    private static String shortUuid(String uuid) {
        return uuid.length() <= 8 ? uuid : uuid.substring(0, 8);
    }

    private record Ban(String bannedUuid, String actorUuid, String reason, String createdAt, String expiresAt) {
    }
}
