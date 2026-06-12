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

public final class IslandUpgradeMenu implements Listener {
    private static final String TITLE = "섬 업그레이드";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandUpgrades(islandId)
            .thenAccept(body -> openSync(plugin, player, upgrades(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 업그레이드를 불러오지 못했습니다."));
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
        if (meta == null || meta.getLore() == null) {
            return;
        }
        String displayName = meta.getDisplayName();
        player.closeInventory();
        if ("섬 은행".equals(displayName)) {
            player.performCommand("섬 은행");
            return;
        }
        if ("새로고침".equals(displayName)) {
            player.performCommand("섬 업그레이드");
            return;
        }
        if ("설정".equals(displayName)) {
            player.performCommand("섬 설정");
            return;
        }
        String key = loreValue(meta, "업그레이드=");
        if (key.isBlank()) {
            return;
        }
        player.performCommand("섬 업그레이드 " + key);
    }

    private static void openSync(Plugin plugin, Player player, List<Upgrade> upgrades) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            int slot = 0;
            for (Upgrade upgrade : upgrades.stream().limit(45).toList()) {
                inventory.setItem(slot++, upgradeItem(upgrade));
            }
            if (upgrades.isEmpty()) {
                inventory.setItem(22, item(Material.BARRIER, "업그레이드 없음", "Core API에 등록된 섬 업그레이드가 없습니다."));
            }
            inventory.setItem(45, item(Material.GOLD_BLOCK, "섬 은행", "/섬 은행"));
            inventory.setItem(49, item(Material.CLOCK, "새로고침", "/섬 업그레이드"));
            inventory.setItem(53, item(Material.COMPARATOR, "설정", "/섬 설정"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack upgradeItem(Upgrade upgrade) {
        Material material = switch (upgrade.type()) {
            case "ISLAND_SIZE" -> Material.GRASS_BLOCK;
            case "MAX_MEMBERS" -> Material.NAME_TAG;
            case "MAX_WARPS" -> Material.ENDER_PEARL;
            case "GENERATOR_LEVEL" -> Material.COBBLESTONE;
            case "REDSTONE_LIMIT" -> Material.REDSTONE;
            case "BANK_LIMIT" -> Material.GOLD_INGOT;
            default -> Material.BEACON;
        };
        return item(material, upgrade.key(), "업그레이드=" + upgrade.key(), "유형: " + upgrade.type(), "현재 레벨: " + upgrade.level(), "클릭하면 다음 레벨 구매를 요청합니다.");
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

    private static List<Upgrade> upgrades(String body) {
        List<Upgrade> upgrades = new ArrayList<>();
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
            String key = text(object, "upgradeKey");
            if (!key.isBlank()) {
                upgrades.add(new Upgrade(key, text(object, "type"), integer(object, "level")));
            }
            index = objectEnd + 1;
        }
        return upgrades;
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

    private static int integer(String body, String key) {
        String needle = "\"" + key + "\":";
        int start = body.indexOf(needle);
        if (start < 0) {
            return 0;
        }
        start += needle.length();
        int end = start;
        while (end < body.length() && Character.isDigit(body.charAt(end))) {
            end++;
        }
        try {
            return Integer.parseInt(body.substring(start, end));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private record Upgrade(String key, String type, int level) {
    }
}
