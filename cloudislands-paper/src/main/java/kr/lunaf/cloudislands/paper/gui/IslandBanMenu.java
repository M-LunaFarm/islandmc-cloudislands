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

public final class IslandBanMenu implements Listener {
    private static final String TITLE = "방문자 밴 목록";
    private final MessageRenderer messages;

    public IslandBanMenu() {
        this(null);
    }

    public IslandBanMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandBans(islandId)
            .thenAccept(body -> openSync(plugin, player, bans(body), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "ban-menu-load-failed", "섬 밴 목록을 불러오지 못했습니다.")));
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
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }
        player.closeInventory();
        if (slot == 49) {
            player.performCommand("섬 밴목록");
            return;
        }
        if (slot == 53) {
            player.performCommand("섬 설정");
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String bannedUuid = loreValue(meta, "대상=");
        if (bannedUuid.isBlank()) {
            return;
        }
        if (event.isRightClick()) {
            player.performCommand("섬 밴해제 " + bannedUuid);
            return;
        }
        player.sendMessage(message(messages, "ban-menu-detail-title", "방문자 밴 상세"));
        if (meta.getLore() != null) {
            for (String line : meta.getLore()) {
                player.sendMessage("- " + line);
            }
        }
    }

    private static void openSync(Plugin plugin, Player player, List<Ban> bans, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            if (bans.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, message(messages, "ban-menu-empty-title", "밴 기록 없음"), message(messages, "ban-menu-empty", "현재 밴된 방문자가 없습니다.")));
            } else {
                for (int index = 0; index < bans.size() && index < 45; index++) {
                    inventory.setItem(index, banItem(bans.get(index), messages));
                }
            }
            inventory.setItem(49, item(Material.CLOCK, message(messages, "ban-menu-refresh-name", "새로고침"), message(messages, "ban-menu-refresh-command", "/섬 밴목록")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "ban-menu-settings-name", "설정"), message(messages, "ban-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack banItem(Ban ban, MessageRenderer messages) {
        return item(Material.BARRIER, message(messages, "ban-menu-title-prefix", "밴 ") + shortUuid(ban.bannedUuid()),
            "대상=" + ban.bannedUuid(),
            message(messages, "ban-menu-actor", "처리자: ") + shortUuid(ban.actorUuid()),
            message(messages, "ban-menu-reason", "사유: ") + (ban.reason().isBlank() ? message(messages, "ban-menu-none", "없음") : ban.reason()),
            ban.createdAt().isBlank() ? message(messages, "ban-menu-no-created-info", "생성 정보 없음") : message(messages, "ban-menu-created-at", "생성 시각: ") + ban.createdAt(),
            ban.expiresAt().isBlank() ? message(messages, "ban-menu-no-expire", "만료 없음") : message(messages, "ban-menu-expires-at", "만료 시각: ") + ban.expiresAt(),
            message(messages, "ban-menu-left-click", "좌클릭: 상세 보기"),
            message(messages, "ban-menu-right-click", "우클릭: 밴 해제"));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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
