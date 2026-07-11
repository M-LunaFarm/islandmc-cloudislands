package kr.seungmin.satisskyfactory.item;

import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CustomItemFactory {
    private final MachineDefinitionService definitions;
    private final NamespacedKey machineTypeKey;
    private final NamespacedKey machineTierKey;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey legacyFactoryItemKey;
    private final NamespacedKey internalUuidKey;

    public CustomItemFactory(JavaPlugin plugin) {
        this(plugin, null);
    }

    public CustomItemFactory(JavaPlugin plugin, MachineDefinitionService definitions) {
        this.definitions = definitions;
        this.machineTypeKey = CustomItemKeys.machineType(plugin);
        this.machineTierKey = CustomItemKeys.machineTier(plugin);
        this.itemIdKey = CustomItemKeys.itemId(plugin);
        this.legacyFactoryItemKey = CustomItemKeys.legacyFactoryItem(plugin);
        this.internalUuidKey = CustomItemKeys.internalUuid(plugin);
    }

    public ItemStack createMachineItem(String typeId, int amount) {
        if (definitions == null) {
            throw new IllegalStateException("Machine definitions are not attached to this item factory");
        }
        MachineDefinition definition = definitions.get(typeId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown machine type: " + typeId));
        return machineItem(definition, amount);
    }

    public ItemStack machineItem(MachineDefinition definition, int amount) {
        ItemStack stack = new ItemStack(definition.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(label(definition.displayName(), NamedTextColor.GOLD));
        meta.lore(List.of(
            label("SatisSkyFactory machine", NamedTextColor.GRAY),
            label(definition.typeId(), NamedTextColor.DARK_GRAY)
        ));
        if (definition.customModelData() > 0) {
            setCustomModelData(meta, definition.customModelData());
        }
        meta.getPersistentDataContainer().set(machineTypeKey, PersistentDataType.STRING, definition.typeId());
        meta.getPersistentDataContainer().set(machineTierKey, PersistentDataType.INTEGER, definition.tier());
        meta.getPersistentDataContainer().set(internalUuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack factoryItem(ItemDefinition item, int amount) {
        ItemStack stack = new ItemStack(item.material(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(label(item.displayName(), NamedTextColor.WHITE));
        if (item.customModelData() > 0) {
            setCustomModelData(meta, item.customModelData());
        }
        if (item.virtualOnly() || item.basePrice() > 0 || !item.tags().isEmpty()) {
            meta.lore(itemLore(item));
        }
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, item.id());
        meta.getPersistentDataContainer().set(legacyFactoryItemKey, PersistentDataType.STRING, item.id());
        meta.getPersistentDataContainer().set(internalUuidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        stack.setItemMeta(meta);
        return stack;
    }

    public Optional<String> machineType(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(machineTypeKey, PersistentDataType.STRING));
    }

    public boolean isMachineItem(ItemStack stack) {
        return machineType(stack).isPresent();
    }

    public Optional<Integer> machineTier(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(machineTierKey, PersistentDataType.INTEGER));
    }

    public Optional<String> internalUuid(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        return Optional.ofNullable(pdc.get(internalUuidKey, PersistentDataType.STRING));
    }

    public Optional<String> factoryItemId(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String itemId = pdc.get(itemIdKey, PersistentDataType.STRING);
        if (itemId != null) {
            return Optional.of(itemId);
        }
        return Optional.ofNullable(pdc.get(legacyFactoryItemKey, PersistentDataType.STRING));
    }

    private List<Component> itemLore(ItemDefinition item) {
        java.util.ArrayList<Component> lore = new java.util.ArrayList<>();
        if (item.virtualOnly()) {
            lore.add(label("Virtual factory item", NamedTextColor.DARK_GRAY));
        }
        if (item.basePrice() > 0) {
            lore.add(label("Base price: " + item.basePrice(), NamedTextColor.GRAY));
        }
        if (!item.tags().isEmpty()) {
            lore.add(label("Tags: " + String.join(", ", item.tags()), NamedTextColor.DARK_GRAY));
        }
        return lore;
    }

    private static Component label(String text, NamedTextColor color) {
        return Component.text(text == null ? "" : text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static void setCustomModelData(ItemMeta meta, int value) {
        var component = meta.getCustomModelDataComponent();
        component.setFloats(List.of((float) value));
        meta.setCustomModelDataComponent(component);
    }
}
