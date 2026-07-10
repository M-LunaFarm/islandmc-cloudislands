package kr.seungmin.satisskyfactory.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.storage.StorageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class AdminGiveCommands {
    private final FactoryIslandService islands;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final CustomItemFactory itemFactory;
    private final ItemRegistry items;
    private final MessageService messages;
    private final Predicate<String> featureEnabled;

    AdminGiveCommands(FactoryIslandService islands, MachineDefinitionService definitions, StorageService storage,
                      CustomItemFactory itemFactory, ItemRegistry items, MessageService messages,
                      Predicate<String> featureEnabled) {
        this.islands = islands;
        this.definitions = definitions;
        this.storage = storage;
        this.itemFactory = itemFactory;
        this.items = items;
        this.messages = messages;
        this.featureEnabled = featureEnabled;
    }

    void giveMachine(CommandSender sender, String[] args) {
        if (args.length < 4) {
            messages.send(sender, "admin-give-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        definitions.get(args[3]).ifPresentOrElse(definition -> {
            long amount = parseLong(args, 4, 1);
            if (amount <= 0) {
                messages.send(sender, "invalid-amount");
                return;
            }
            long returned = giveMachineItem(target, definition, amount);
            if (returned > 0) {
                messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-machine"));
    }

    void giveItem(CommandSender sender, String[] args) {
        if (args.length < 5) {
            messages.send(sender, "admin-giveitem-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        items.get(args[3]).ifPresentOrElse(item -> {
            long amount = parseLong(args, 4, 0);
            if (amount <= 0) {
                messages.send(sender, "invalid-amount");
                return;
            }
            if (item.virtualOnly()) {
                if (!giveVirtualOnlyItem(sender, target, item.id(), amount)) {
                    return;
                }
            } else {
                long returned = giveInventoryItem(target, item.id(), amount);
                if (returned > 0) {
                    messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
                }
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-item"));
    }

    List<String> machineTypeIds() {
        return definitions == null ? List.of() : definitions.all().stream().map(MachineDefinition::typeId).sorted().toList();
    }

    List<String> itemIds() {
        return items == null ? List.of() : items.all().keySet().stream().sorted().toList();
    }

    private long giveMachineItem(Player target, MachineDefinition definition, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemFactory.createMachineItem(definition.typeId(), stackAmount(definition.material(), remaining));
            int delivered = stack.getAmount();
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                    + Math.max(0, remaining - delivered);
            }
            remaining -= delivered;
        }
        return 0;
    }

    private boolean giveVirtualOnlyItem(CommandSender sender, Player target, String itemId, long amount) {
        if (!requireFeature(sender, "storage")) {
            return false;
        }
        return islands.context(target).map(context -> {
            var inventory = storage.islandStorageIfAllowed(context.factoryIsland().islandUuid()).orElse(null);
            if (inventory == null) {
                messages.send(sender, "feature-disabled", Map.of("feature", "storage"));
                return false;
            }
            if (!inventory.add(itemId, amount)) {
                messages.send(sender, "storage-full");
                return false;
            }
            if (!storage.saveIfAllowed(inventory)) {
                inventory.remove(itemId, amount);
                messages.send(sender, "feature-disabled", Map.of("feature", "storage"));
                return false;
            }
            return true;
        }).orElseGet(() -> {
            messages.send(sender, "no-island");
            return false;
        });
    }

    private long giveInventoryItem(Player player, String itemId, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemStack(itemId, remaining);
            int delivered = stack.getAmount();
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                    + Math.max(0, remaining - delivered);
            }
            remaining -= delivered;
        }
        return 0;
    }

    private ItemStack itemStack(String itemId, long amount) {
        return items.get(itemId)
            .map(item -> itemFactory.factoryItem(item, stackAmount(item.material(), amount)))
            .orElseGet(() -> {
                Material material = material(itemId);
                return new ItemStack(material, stackAmount(material, amount));
            });
    }

    private int stackAmount(Material material, long amount) {
        int maxStackSize = Math.max(1, material.getMaxStackSize());
        return (int) Math.max(1, Math.min(maxStackSize, amount));
    }

    private Material material(String itemId) {
        Material material = Material.matchMaterial(itemId.toUpperCase(Locale.ROOT));
        return material == null ? Material.PAPER : material;
    }

    private boolean requireFeature(CommandSender sender, String feature) {
        if (enabled(feature)) {
            return true;
        }
        messages.send(sender, "feature-disabled", Map.of("feature", feature));
        return false;
    }

    private boolean enabled(String feature) {
        if (featureEnabled == null) {
            return true;
        }
        try {
            return featureEnabled.test(feature);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private long parseLong(String[] args, int index, long fallback) {
        if (args.length <= index) {
            return fallback;
        }
        try {
            return Long.parseLong(args[index]);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
