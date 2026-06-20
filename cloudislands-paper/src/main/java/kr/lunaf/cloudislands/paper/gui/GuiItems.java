package kr.lunaf.cloudislands.paper.gui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class GuiItems {
    private static final NamespacedKey ACTION_ID = new NamespacedKey("cloudislands", "action-id");
    private static final NamespacedKey DATA_PREFIX = new NamespacedKey("cloudislands", "data");

    private GuiItems() {
    }

    public static ItemStack action(Material material, String name, String actionId, String... lore) {
        return action(material, name, actionId, Map.of(), lore);
    }

    public static ItemStack action(Material material, String name, String actionId, Map<String, String> data, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(List.of(lore));
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(ACTION_ID, PersistentDataType.STRING, actionId);
            if (!data.isEmpty()) {
                pdc.set(DATA_PREFIX, PersistentDataType.STRING, encode(data));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static String actionId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return "";
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(ACTION_ID, PersistentDataType.STRING);
        return value == null ? "" : value;
    }

    public static Map<String, String> data(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Map.of();
        }
        String encoded = item.getItemMeta().getPersistentDataContainer().get(DATA_PREFIX, PersistentDataType.STRING);
        return decode(encoded);
    }

    public static boolean topInventoryClick(InventoryClickEvent event) {
        return event.getClickedInventory() != null
            && event.getClickedInventory() == event.getView().getTopInventory()
            && event.getRawSlot() >= 0
            && event.getRawSlot() < event.getView().getTopInventory().getSize();
    }

    public static boolean menuClick(InventoryClickEvent event, String menuId) {
        return topInventoryClick(event) && GuiInventories.isMenu(event.getView().getTopInventory(), menuId);
    }

    private static String encode(Map<String, String> data) {
        StringBuilder builder = new StringBuilder();
        data.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(escape(entry.getKey())).append('=').append(escape(entry.getValue()));
            });
        return builder.toString();
    }

    private static Map<String, String> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Map.of();
        }
        Map<String, String> data = new HashMap<>();
        for (String line : encoded.split("\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            data.put(unescape(line.substring(0, separator)), unescape(line.substring(separator + 1)));
        }
        return Map.copyOf(data);
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\n", "\\n").replace("=", "\\e");
    }

    private static String unescape(String value) {
        StringBuilder builder = new StringBuilder();
        boolean escaped = false;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (escaped) {
                builder.append(ch == 'n' ? '\n' : ch == 'e' ? '=' : ch);
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else {
                builder.append(ch);
            }
        }
        if (escaped) {
            builder.append('\\');
        }
        return builder.toString();
    }
}
