package kr.lunaf.cloudislands.paper.gui;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
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

public final class IslandFlagMenu implements Listener {
    private static final String TITLE = "섬 플래그 설정";

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        client.listIslandFlags(islandId)
            .thenAccept(body -> openSync(plugin, player, flags(body)))
            .exceptionally(error -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("섬 플래그를 불러오지 못했습니다."));
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
        String flag = "";
        for (String line : meta.getLore()) {
            if (line.startsWith("flag=")) {
                flag = line.substring("flag=".length());
                break;
            }
        }
        if (flag.isBlank()) {
            return;
        }
        player.closeInventory();
        player.performCommand("섬 플래그설정 " + flag + " " + (!event.isRightClick()));
    }

    private static void openSync(Plugin plugin, Player player, Map<IslandFlag, String> values) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
            int slot = 0;
            for (IslandFlag flag : IslandFlag.values()) {
                inventory.setItem(slot++, flagItem(flag, values.get(flag)));
            }
            player.openInventory(inventory);
        });
    }

    private static ItemStack flagItem(IslandFlag flag, String value) {
        String normalized = value == null ? "" : value;
        Material material = normalized.equalsIgnoreCase("true") ? Material.LIME_DYE : normalized.equalsIgnoreCase("false") ? Material.RED_DYE : Material.GRAY_DYE;
        String state = normalized.isBlank() ? "기본값" : normalized;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(flag.name());
            meta.setLore(java.util.List.of("flag=" + flag.name(), "현재 값: " + state, "좌클릭: true, 우클릭: false"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Map<IslandFlag, String> flags(String body) {
        Map<IslandFlag, String> values = new EnumMap<>(IslandFlag.class);
        int flagsStart = body == null ? -1 : body.indexOf("\"flags\":{");
        if (flagsStart < 0) {
            return values;
        }
        int index = body.indexOf('{', flagsStart);
        if (index < 0) {
            return values;
        }
        while (index < body.length()) {
            int keyStart = body.indexOf('"', index + 1);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = body.indexOf('"', keyStart + 1);
            if (keyEnd < 0) {
                break;
            }
            String key = body.substring(keyStart + 1, keyEnd);
            int valueStart = body.indexOf('"', keyEnd + 1);
            int valueEnd = valueStart < 0 ? -1 : body.indexOf('"', valueStart + 1);
            if (valueStart < 0 || valueEnd < 0) {
                break;
            }
            try {
                values.put(IslandFlag.valueOf(key), body.substring(valueStart + 1, valueEnd).replace("\\\"", "\"").replace("\\\\", "\\"));
            } catch (IllegalArgumentException ignored) {
            }
            index = valueEnd + 1;
        }
        return values;
    }
}
