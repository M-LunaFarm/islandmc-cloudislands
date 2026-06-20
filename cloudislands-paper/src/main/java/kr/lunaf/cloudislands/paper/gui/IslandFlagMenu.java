package kr.lunaf.cloudislands.paper.gui;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
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

public final class IslandFlagMenu implements Listener {
    private static final String TITLE_KEY = "flag-menu-title";
    private static final String TITLE = "섬 플래그 설정";
    private final MessageRenderer messages;

    public IslandFlagMenu() {
        this(null);
    }

    public IslandFlagMenu(MessageRenderer messages) {
        this.messages = messages;
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId) {
        open(plugin, client, player, islandId, null);
    }

    public static void open(Plugin plugin, CoreApiClient client, Player player, UUID islandId, MessageRenderer messages) {
        client.listIslandFlags(islandId)
            .thenAccept(body -> openSync(plugin, player, flags(body), messages))
            .exceptionally(error -> {
                kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> player.sendMessage(message(messages, "flag-menu-load-failed", "섬 플래그를 불러오지 못했습니다.")));
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
            GuiActionRegistry.execute(player, "island.flags.open", GuiClick.from(event));
            return;
        }
        if (slot == 53) {
            GuiActionRegistry.execute(player, "island.settings.open", GuiClick.from(event));
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return;
        }
        String flag = "";
        for (String line : meta.getLore()) {
            if (line.startsWith("플래그=")) {
                flag = line.substring("플래그=".length());
                break;
            }
        }
        if (flag.isBlank()) {
            return;
        }
        GuiActionRegistry.execute(player, "island.flag.set", java.util.Map.of("flag", flag), GuiClick.from(event));
    }

    private static void openSync(Plugin plugin, Player player, Map<IslandFlag, String> values, MessageRenderer messages) {
        kr.lunaf.cloudislands.paper.platform.scheduler.PaperSchedulers.run(plugin, () -> {
            Inventory inventory = Bukkit.createInventory(null, 54, message(messages, TITLE_KEY, TITLE));
            int slot = 0;
            for (IslandFlag flag : IslandFlag.values()) {
                inventory.setItem(slot++, flagItem(flag, values.get(flag), messages));
            }
            inventory.setItem(49, item(Material.CLOCK, message(messages, "flag-menu-refresh-name", "새로고침"), message(messages, "flag-menu-refresh-command", "/섬 플래그")));
            inventory.setItem(53, item(Material.COMPARATOR, message(messages, "flag-menu-settings-name", "설정"), message(messages, "flag-menu-settings-command", "/섬 설정")));
            player.openInventory(inventory);
        });
    }

    private static ItemStack flagItem(IslandFlag flag, String value, MessageRenderer messages) {
        String normalized = value == null ? "" : value;
        Material material = normalized.equalsIgnoreCase("true") ? Material.LIME_DYE : normalized.equalsIgnoreCase("false") ? Material.RED_DYE : Material.GRAY_DYE;
        String state = normalized.isBlank() ? message(messages, "flag-menu-default", "기본값") : normalized.equalsIgnoreCase("true") ? message(messages, "flag-menu-allow", "허용") : normalized.equalsIgnoreCase("false") ? message(messages, "flag-menu-deny", "거부") : normalized;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(flag.name());
            meta.setLore(java.util.List.of("플래그=" + flag.name(), message(messages, "flag-menu-current-value", "현재 값: ") + state, message(messages, "flag-menu-click-actions", "좌클릭: 허용, 우클릭: 거부")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack item(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(java.util.List.of(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
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
