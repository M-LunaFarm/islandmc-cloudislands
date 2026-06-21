package kr.lunaf.cloudislands.paper.gui;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import kr.lunaf.cloudislands.paper.message.MessageRenderer;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

public final class GuiMenuRenderer {
    private GuiMenuRenderer() {
    }

    public static Inventory render(GuiMenuDefinition definition, MessageRenderer messages, String fallbackTitle) {
        return render(definition, messages, fallbackTitle, item -> true);
    }

    public static Inventory render(GuiMenuDefinition definition, MessageRenderer messages, String fallbackTitle, Predicate<GuiMenuDefinition.MenuItem> visible) {
        Inventory inventory = GuiInventories.create(definition.id(), definition.size(), message(messages, definition.titleKey(), fallbackTitle));
        populate(inventory, definition, messages, visible);
        return inventory;
    }

    public static Inventory render(GuiMenuDefinition definition, GuiSession session, MessageRenderer messages, String fallbackTitle, Predicate<GuiMenuDefinition.MenuItem> visible) {
        Inventory inventory = GuiInventories.create(definition.id(), session, definition.size(), message(messages, definition.titleKey(), fallbackTitle));
        populate(inventory, definition, messages, visible);
        return inventory;
    }

    public static void populate(Inventory inventory, GuiMenuDefinition definition, MessageRenderer messages, Predicate<GuiMenuDefinition.MenuItem> visible) {
        Predicate<GuiMenuDefinition.MenuItem> effectiveVisible = visible == null ? item -> true : visible;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int currentSlot = slot;
            definition.itemAt(slot)
                .filter(effectiveVisible)
                .ifPresent(item -> inventory.setItem(currentSlot, item(definition, item, messages)));
        }
    }

    public static String message(MessageRenderer messages, String key, String fallback) {
        if (messages == null) {
            return fallback;
        }
        String rendered = messages.plain(key);
        return rendered.isBlank() ? fallback : rendered;
    }

    public static Material material(String key) {
        try {
            return Material.valueOf(key == null ? "STONE" : key);
        } catch (RuntimeException ignored) {
            return Material.STONE;
        }
    }

    public static org.bukkit.inventory.ItemStack item(GuiMenuDefinition definition, GuiMenuDefinition.MenuItem item, MessageRenderer messages) {
        return item(definition, item, messages, item.data());
    }

    public static org.bukkit.inventory.ItemStack item(GuiMenuDefinition definition, GuiMenuDefinition.MenuItem item, MessageRenderer messages, Map<String, String> data) {
        return item(definition, item, messages, data, List.of());
    }

    public static org.bukkit.inventory.ItemStack item(GuiMenuDefinition definition, GuiMenuDefinition.MenuItem item, MessageRenderer messages, Map<String, String> data, List<String> extraLore) {
        return item(definition, item, messages, data, extraLore, null);
    }

    public static org.bukkit.inventory.ItemStack item(GuiMenuDefinition definition, GuiMenuDefinition.MenuItem item, MessageRenderer messages, Map<String, String> data, List<String> extraLore, String actionIdOverride) {
        return item(definition, item, messages, item.materialKey(), data, extraLore, actionIdOverride);
    }

    public static org.bukkit.inventory.ItemStack stateItem(GuiMenuDefinition definition, GuiMenuDefinition.MenuItem item, MessageRenderer messages, boolean active, Map<String, String> data, List<String> extraLore) {
        return item(definition, item, messages, item.materialKey(active), data, extraLore, null);
    }

    private static org.bukkit.inventory.ItemStack item(GuiMenuDefinition definition, GuiMenuDefinition.MenuItem item, MessageRenderer messages, String materialKey, Map<String, String> data, List<String> extraLore, String actionIdOverride) {
        java.util.LinkedHashMap<String, String> mergedData = new java.util.LinkedHashMap<>(item.data());
        if (data != null) {
            mergedData.putAll(data);
        }
        java.util.ArrayList<String> renderedLore = new java.util.ArrayList<>(lore(item, messages));
        if (extraLore != null) {
            for (String line : extraLore) {
                if (line != null && !line.isBlank()) {
                    renderedLore.add(line);
                }
            }
        }
        return GuiItems.action(
            material(materialKey),
            message(messages, item.nameKey(), item.fallbackName()),
            actionIdOverride == null ? definition.action(item.actionKey(), item.actionKey()) : actionIdOverride,
            mergedData,
            renderedLore.toArray(String[]::new)
        );
    }

    private static List<String> lore(GuiMenuDefinition.MenuItem item, MessageRenderer messages) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>();
        int count = Math.max(item.loreKeys().size(), item.fallbackLore().size());
        for (int index = 0; index < count; index++) {
            String key = index < item.loreKeys().size() ? item.loreKeys().get(index) : "";
            String fallback = index < item.fallbackLore().size() ? item.fallbackLore().get(index) : "";
            String line = message(messages, key, fallback);
            if (!line.isBlank()) {
                lore.add(line);
            }
        }
        return List.copyOf(lore);
    }
}
