package kr.seungmin.satisskyfactory.command;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineDefinition;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.util.NumberFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public final class AdminFactoryCommand {
    private static final int HELP_PAGE_SIZE = 8;
    private static final List<String> FEATURE_KEYS = List.of(
            "commands",
            "machines",
            "factories",
            "generators",
            "upgrades",
            "missions",
            "menus",
            "gui",
            "lifecycle",
            "resource-nodes",
            "market",
            "contracts",
            "research",
            "maintenance",
            "placeholders"
    );
    private static final List<String> HELP_COMMANDS = List.of(
            "factory admin help [page]",
            "factory admin command list [page]",
            "factory admin reload",
            "factory admin features",
            "factory admin give <player> <machineType> [amount]",
            "factory admin giveitem <player> <itemId> <amount>",
            "factory admin addresearch <player> <amount>",
            "factory admin setdebt <player> <amount>",
            "factory admin charge <player>",
            "factory admin gennodes <player>",
            "factory admin debug island",
            "factory admin debug networks",
            "factory admin removehere",
            "factory admin repairhere"
    );
    private final FactoryIslandService islands;
    private final MachineService machines;
    private final MachineDefinitionService definitions;
    private final StorageService storage;
    private final ResourceNodeService nodes;
    private final SkyblockProvider skyblock;
    private final MaintenanceService maintenance;
    private final ResearchService research;
    private final PowerNetworkService power;
    private final CustomItemFactory itemFactory;
    private final ItemRegistry items;
    private final MessageService messages;
    private final Predicate<String> featureEnabled;
    private final Runnable reload;

    public AdminFactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                               StorageService storage, ResourceNodeService nodes, SkyblockProvider skyblock,
                               MaintenanceService maintenance, ResearchService research, PowerNetworkService power,
                               CustomItemFactory itemFactory, ItemRegistry items,
                               MessageService messages, Predicate<String> featureEnabled, Runnable reload) {
        this.islands = islands;
        this.machines = machines;
        this.definitions = definitions;
        this.storage = storage;
        this.nodes = nodes;
        this.skyblock = skyblock;
        this.maintenance = maintenance;
        this.research = research;
        this.power = power;
        this.itemFactory = itemFactory;
        this.items = items;
        this.messages = messages;
        this.featureEnabled = featureEnabled;
        this.reload = reload;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            if (!sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
            help(sender, 1);
            return true;
        }
        String subcommand = args[1].toLowerCase(Locale.ROOT);
        if (subcommand.equals("debug")) {
            if (!sender.hasPermission("satisskyfactory.debug") && !sender.hasPermission("satisskyfactory.admin")) {
                messages.send(sender, "no-permission");
                return true;
            }
        } else if (!sender.hasPermission("satisskyfactory.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        switch (subcommand) {
            case "help", "commands", "command", "command-list", "명령어", "명령어목록" -> help(sender, helpPage(args));
            case "reload" -> {
                reload.run();
                messages.send(sender, "reloaded");
            }
            case "features" -> showFeatures(sender);
            case "give" -> {
                if (requireFeature(sender, "machines")) {
                    giveMachine(sender, args);
                }
            }
            case "giveitem" -> {
                if (requireFeature(sender, "machines")) {
                    giveItem(sender, args);
                }
            }
            case "addresearch" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "research")) {
                    return;
                }
                research.addResearch(island, parseLong(args, 3, 0));
                islands.save(island);
                messages.send(sender, "admin-research-updated");
            });
            case "setdebt" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "maintenance")) {
                    return;
                }
                maintenance.setDebt(island, parseLong(args, 3, 0));
                islands.save(island);
                messages.send(sender, "admin-debt-updated");
            });
            case "charge" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "maintenance")) {
                    return;
                }
                islands.context(target).ifPresent(context -> maintenance.chargeNow(island, target, context.islandRef().raw()));
                islands.save(island);
                messages.send(sender, "admin-maintenance-charged");
            });
            case "gennodes" -> withPlayerContext(sender, args, 2, (target, island) -> {
                if (!requireFeature(sender, "resource-nodes")) {
                    return;
                }
                nodes.generateIfMissing(island.islandUuid(), target.getLocation(), location -> isInsideIsland(location, island));
                messages.send(sender, "admin-nodes-generated");
            });
            case "debug" -> debug(sender, args);
            case "removehere" -> {
                if (requireFeature(sender, "machines")) {
                    removeHere(sender);
                }
            }
            case "repairhere" -> {
                if (requireFeature(sender, "maintenance")) {
                    repairHere(sender);
                }
            }
            default -> messages.send(sender, "unknown-admin-command");
        }
        return true;
    }

    public List<String> complete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> values = new ArrayList<>();
            values.add("reload");
            values.add("help");
            values.add("commands");
            values.add("command");
            values.add("command-list");
            values.add("명령어");
            values.add("명령어목록");
            values.add("debug");
            values.add("features");
            if (enabled("machines")) {
                values.add("give");
                values.add("giveitem");
                values.add("removehere");
            }
            if (enabled("research")) {
                values.add("addresearch");
            }
            if (enabled("maintenance")) {
                values.add("setdebt");
                values.add("charge");
                values.add("repairhere");
            }
            if (enabled("resource-nodes")) {
                values.add("gennodes");
            }
            return filter(values, args[1]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("command")) {
            return filter(List.of("list"), args[2]);
        }
        if ((args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem") || args[1].equalsIgnoreCase("removehere")) && !enabled("machines")) {
            return new ArrayList<>();
        }
        if (args[1].equalsIgnoreCase("addresearch") && !enabled("research")) {
            return new ArrayList<>();
        }
        if ((args[1].equalsIgnoreCase("setdebt") || args[1].equalsIgnoreCase("charge") || args[1].equalsIgnoreCase("repairhere")) && !enabled("maintenance")) {
            return new ArrayList<>();
        }
        if (args[1].equalsIgnoreCase("gennodes") && !enabled("resource-nodes")) {
            return new ArrayList<>();
        }
        if (args.length == 3 && needsPlayer(args[1])) {
            return filter(onlinePlayerNames(), args[2]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("give")) {
            return filter(definitions.all().stream().map(MachineDefinition::typeId).toList(), args[3]);
        }
        if (args.length == 4 && args[1].equalsIgnoreCase("giveitem")) {
            return filter(itemIds(), args[3]);
        }
        if (args.length == 4 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem")
                || args[1].equalsIgnoreCase("addresearch") || args[1].equalsIgnoreCase("setdebt"))) {
            return filter(amountSuggestions(), args[3]);
        }
        if (args.length == 5 && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("giveitem"))) {
            return filter(amountSuggestions(), args[4]);
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("debug")) {
            return filter(List.of("island", "networks"), args[2]);
        }
        return new ArrayList<>();
    }

    private boolean isInsideIsland(org.bukkit.Location location, FactoryIsland island) {
        return skyblock.getIslandAt(location)
                .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                .orElse(false);
    }

    private void giveMachine(CommandSender sender, String[] args) {
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

    private void giveItem(CommandSender sender, String[] args) {
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
                long returned = giveVirtualItem(target, item.id(), amount);
                if (returned > 0) {
                    messages.send(sender, "target-inventory-full", Map.of("amount", String.valueOf(returned)));
                }
            }
            messages.send(sender, "given");
        }, () -> messages.send(sender, "unknown-item"));
    }

    private long giveMachineItem(Player target, MachineDefinition definition, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemFactory.createMachineItem(definition.typeId(), stackAmount(definition.material(), remaining));
            int stackAmount = stack.getAmount();
            Map<Integer, ItemStack> overflow = target.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                        + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
        }
        return 0;
    }

    private boolean giveVirtualOnlyItem(CommandSender sender, Player target, String itemId, long amount) {
        return islands.context(target).map(context -> {
            var inventory = storage.islandStorage(context.factoryIsland().islandUuid());
            if (!inventory.add(itemId, amount)) {
                messages.send(sender, "storage-full");
                return false;
            }
            storage.save(inventory);
            return true;
        }).orElseGet(() -> {
            messages.send(sender, "no-island");
            return false;
        });
    }

    private long giveVirtualItem(Player player, String itemId, long amount) {
        long remaining = amount;
        while (remaining > 0) {
            ItemStack stack = itemStack(itemId, remaining);
            int stackAmount = stack.getAmount();
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                return overflow.values().stream().mapToLong(ItemStack::getAmount).sum()
                        + Math.max(0, remaining - stackAmount);
            }
            remaining -= stackAmount;
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

    private void debug(CommandSender sender, String[] args) {
        if (args.length < 3 || !(sender instanceof Player player)) {
            return;
        }
        islands.context(player).ifPresent(context -> {
            if (args[2].equalsIgnoreCase("island")) {
                messages.send(sender, "debug-island", Map.of("island", context.factoryIsland().islandUuid().toString()));
            } else if (args[2].equalsIgnoreCase("networks")) {
                if (!requireFeature(sender, "machines")) {
                    return;
                }
                var state = power.state(context.factoryIsland().islandUuid());
                messages.send(sender, "debug-networks", Map.of(
                        "machines", String.valueOf(machines.byIsland(context.factoryIsland().islandUuid()).size()),
                        "ratio", NumberFormatter.ratio(state.ratio()),
                        "generation", NumberFormatter.decimal(state.generation(), 1),
                        "consumption", NumberFormatter.decimal(state.consumption(), 1),
                        "battery", NumberFormatter.decimal(state.batteryStored(), 1) + "/" + NumberFormatter.whole(state.batteryCapacity())));
            }
        });
    }

    private void removeHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            machines.forceRemove(machine);
            block.setType(Material.AIR, false);
            messages.send(sender, "machine-removed-admin");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private void repairHere(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(sender, "no-target-block");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            repair(machine);
            messages.send(sender, "machine-repaired");
        }, () -> messages.send(sender, "no-machine-here"));
    }

    private void repair(MachineInstance machine) {
        machine.wear(0.0);
        machine.status(MachineStatus.SLEEPING);
        machines.save(machine);
    }

    private boolean requireFeature(CommandSender sender, String feature) {
        if (enabled(feature)) {
            return true;
        }
        messages.send(sender, "feature-disabled", Map.of("feature", feature));
        return false;
    }

    private boolean enabled(String feature) {
        return featureEnabled == null || featureEnabled.test(feature);
    }

    private void showFeatures(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-features-title"));
        for (String feature : FEATURE_KEYS) {
            sender.sendMessage(messages.raw("admin-features-entry", Map.of(
                    "feature", feature,
                    "enabled", Boolean.toString(enabled(feature))
            )));
        }
    }

    private void help(CommandSender sender, int page) {
        List<String> commands = visibleHelpCommands();
        int maxPage = Math.max(1, (commands.size() + HELP_PAGE_SIZE - 1) / HELP_PAGE_SIZE);
        int safePage = Math.max(1, Math.min(page, maxPage));
        int from = (safePage - 1) * HELP_PAGE_SIZE;
        int to = Math.min(commands.size(), from + HELP_PAGE_SIZE);
        sender.sendMessage(messages.raw("admin-command-list-title", Map.of("page", String.valueOf(safePage), "pages", String.valueOf(maxPage))));
        for (String command : commands.subList(from, to)) {
            sender.sendMessage(messages.raw("command-list-entry", Map.of("command", command)));
        }
        if (safePage < maxPage) {
            sender.sendMessage(messages.raw("command-list-entry", Map.of("command", "factory admin command list " + (safePage + 1))));
        }
    }

    private List<String> visibleHelpCommands() {
        List<String> values = new ArrayList<>();
        for (String command : HELP_COMMANDS) {
            if (commandRequiresDisabledFeature(command)) {
                continue;
            }
            values.add(command);
        }
        return values;
    }

    private boolean commandRequiresDisabledFeature(String command) {
        return (command.contains(" give ") || command.contains(" giveitem ") || command.contains(" removehere")) && !enabled("machines")
                || command.contains(" addresearch ") && !enabled("research")
                || (command.contains(" setdebt ") || command.contains(" charge ") || command.contains(" repairhere")) && !enabled("maintenance")
                || command.contains(" gennodes ") && !enabled("resource-nodes")
                || command.contains(" debug networks") && !enabled("machines");
    }

    private int helpPage(String[] args) {
        if (args.length > 3 && args[1].equalsIgnoreCase("command") && (args[2].equalsIgnoreCase("list") || args[2].equals("목록"))) {
            return (int) parseLong(args, 3, 1);
        }
        if (args.length > 2) {
            return (int) parseLong(args, 2, 1);
        }
        return 1;
    }

    private void withPlayerContext(CommandSender sender, String[] args, int playerIndex, AdminContextConsumer consumer) {
        if (args.length <= playerIndex) {
            messages.send(sender, "player-required");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[playerIndex]);
        if (target == null) {
            messages.send(sender, "player-not-found");
            return;
        }
        islands.context(target).ifPresentOrElse(context -> consumer.accept(target, context.factoryIsland()), () -> messages.send(sender, "no-island"));
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

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private List<String> itemIds() {
        return items.all().keySet().stream().sorted().toList();
    }

    private List<String> onlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> amountSuggestions() {
        return List.of("1", "8", "16", "32", "64", "256", "1024");
    }

    private boolean needsPlayer(String adminSubcommand) {
        return adminSubcommand.equalsIgnoreCase("give")
                || adminSubcommand.equalsIgnoreCase("giveitem")
                || adminSubcommand.equalsIgnoreCase("addresearch")
                || adminSubcommand.equalsIgnoreCase("setdebt")
                || adminSubcommand.equalsIgnoreCase("charge")
                || adminSubcommand.equalsIgnoreCase("gennodes");
    }

    @FunctionalInterface
    private interface AdminContextConsumer {
        void accept(Player player, FactoryIsland island);
    }
}
