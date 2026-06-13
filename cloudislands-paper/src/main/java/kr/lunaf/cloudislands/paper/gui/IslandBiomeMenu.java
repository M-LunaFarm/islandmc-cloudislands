package kr.lunaf.cloudislands.paper.gui;

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

public final class IslandBiomeMenu implements Listener {
    private static final String TITLE = "섬 바이옴";
    private final MessageRenderer messages;
    private static final List<String> BIOMES = List.of(
        "minecraft:plains",
        "minecraft:forest",
        "minecraft:cherry_grove",
        "minecraft:desert",
        "minecraft:snowy_plains",
        "minecraft:jungle",
        "minecraft:swamp",
        "minecraft:badlands",
        "minecraft:taiga",
        "minecraft:mushroom_fields"
    );

    public IslandBiomeMenu() {
        this(null);
    }

    public IslandBiomeMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.islandBiome(islandId)
            .thenAccept(body -> openSync(plugin, player, text(body, "biomeKey"), messages))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(messages, "biome-menu-load-failed", "섬 바이옴을 불러오지 못했습니다.")));
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
        if ("현재 바이옴".equals(displayName)) {
            player.performCommand("섬 바이옴정보");
            return;
        }
        if ("새로고침".equals(displayName)) {
            player.performCommand("섬 바이옴");
            return;
        }
        if ("설정".equals(displayName)) {
            player.performCommand("섬 설정");
            return;
        }
        if ("메인 메뉴".equals(displayName)) {
            player.performCommand("섬 메뉴");
            return;
        }
        String biomeKey = loreValue(meta, "biomeKey=");
        if (biomeKey.isBlank()) {
            return;
        }
        player.performCommand("섬 바이옴 " + biomeKey);
    }

    private static void openSync(Plugin plugin, Player player, String currentBiome, MessageRenderer messages) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
            int slot = 9;
            for (String biome : BIOMES) {
                inventory.setItem(slot++, biomeItem(biome, biome.equalsIgnoreCase(currentBiome), messages));
            }
            inventory.setItem(4, item(Material.GRASS_BLOCK, "현재 바이옴", currentBiome.isBlank() ? message(messages, "biome-menu-not-set", "설정 없음") : currentBiome));
            inventory.setItem(22, item(Material.CLOCK, "새로고침", "/섬 바이옴"));
            inventory.setItem(24, item(Material.COMPARATOR, "설정", "/섬 설정"));
            inventory.setItem(26, item(Material.COMPASS, "메인 메뉴", "/섬 메뉴"));
            player.openInventory(inventory);
        });
    }

    private static ItemStack biomeItem(String biome, boolean selected, MessageRenderer messages) {
        Material material = selected ? Material.LIME_DYE : Material.GRASS_BLOCK;
        return item(material, biome, "biomeKey=" + biome, selected ? message(messages, "biome-menu-selected", "현재 적용됨") : message(messages, "biome-menu-click-to-change", "클릭하면 이 바이옴으로 변경합니다."));
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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
        for (String line : meta.getLore()) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
        }
        return "";
    }

    private static String text(String body, String key) {
        String needle = "\"" + key + "\":\"";
        int start = body == null ? -1 : body.indexOf(needle);
        if (start < 0) {
            return "";
        }
        start += needle.length();
        int end = body.indexOf('"', start);
        return end < start ? "" : body.substring(start, end).replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
