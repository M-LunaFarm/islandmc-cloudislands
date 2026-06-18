package kr.seungmin.satisskyfactory.command;

import kr.lunaf.cloudislands.protocol.command.CommandListPolicy;
import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.contract.ContractService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
import kr.seungmin.satisskyfactory.gui.FactoryGuiService;
import kr.seungmin.satisskyfactory.hook.SkyblockProvider;
import kr.seungmin.satisskyfactory.item.CustomItemFactory;
import kr.seungmin.satisskyfactory.item.ItemDefinition;
import kr.seungmin.satisskyfactory.item.ItemRegistry;
import kr.seungmin.satisskyfactory.machine.FactoryIslandService;
import kr.seungmin.satisskyfactory.machine.IslandBoostService;
import kr.seungmin.satisskyfactory.machine.MachineDefinitionService;
import kr.seungmin.satisskyfactory.machine.MachineService;
import kr.seungmin.satisskyfactory.machine.MaintenanceService;
import kr.seungmin.satisskyfactory.market.MarketService;
import kr.seungmin.satisskyfactory.model.FactoryContext;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.node.ResourceNodeService;
import kr.seungmin.satisskyfactory.power.PowerNetworkService;
import kr.seungmin.satisskyfactory.research.ResearchService;
import kr.seungmin.satisskyfactory.storage.StorageService;
import kr.seungmin.satisskyfactory.util.NumberFormatter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Supplier;

public final class FactoryCommand implements CommandExecutor, TabCompleter {
    private static final List<String> HELP_COMMANDS = List.of(
            "factory help [page]",
            "factory list [page]",
            "factory command list [page]",
            "factory status",
            "factory main",
            "factory machines",
            "factory storage",
            "factory deposit",
            "factory withdraw <itemId> <amount>",
            "factory market",
            "factory sell hand",
            "factory sell <itemId> <amount>",
            "factory contracts",
            "factory contracts complete",
            "factory emergency",
            "factory emergency complete",
            "factory research",
            "factory research unlock <researchId>",
            "factory node scan",
            "factory repair",
            "factory admin command list [page]"
    );
    private final FactoryIslandService islands;
    private final MachineService machines;
    private final StorageService storage;
    private final ResourceNodeService nodes;
    private final SkyblockProvider skyblock;
    private final MarketService market;
    private final ContractService contracts;
    private final MaintenanceService maintenance;
    private final ResearchService research;
    private final IslandBoostService boosts;
    private final PowerNetworkService power;
    private final FactoryGuiService gui;
    private final CustomItemFactory itemFactory;
    private final ItemRegistry items;
    private final MessageService messages;
    private final Predicate<String> featureEnabled;
    private final AdminFactoryCommand adminCommand;
    private final int commandListPageSize;

    public FactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                          StorageService storage, ResourceNodeService nodes, SkyblockProvider skyblock,
                          MarketService market, ContractService contracts,
                          MaintenanceService maintenance, ResearchService research, IslandBoostService boosts,
                          PowerNetworkService power, FactoryGuiService gui, CustomItemFactory itemFactory,
                          ItemRegistry items, MessageService messages, DatabaseService database, Predicate<String> featureEnabled,
                          Supplier<Map<String, String>> integrationMetadata,
                          Supplier<Map<String, String>> addonState,
                          Function<UUID, Map<String, String>> addonIslandState,
                          int commandListPageSize,
                          Runnable reload) {
        this.islands = islands;
        this.machines = machines;
        this.storage = storage;
        this.nodes = nodes;
        this.skyblock = skyblock;
        this.market = market;
        this.contracts = contracts;
        this.maintenance = maintenance;
        this.research = research;
        this.boosts = boosts;
        this.power = power;
        this.gui = gui;
        this.itemFactory = itemFactory;
        this.items = items;
        this.messages = messages;
        this.featureEnabled = featureEnabled;
        this.commandListPageSize = Math.max(1, commandListPageSize);
        this.adminCommand = new AdminFactoryCommand(islands, machines, definitions, storage, nodes, skyblock,
                maintenance, research, power, itemFactory, items, messages, database, featureEnabled, integrationMetadata, addonState, addonIslandState, this.commandListPageSize, reload);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!enabled("commands")) {
            messages.send(sender, "feature-disabled", Map.of("feature", "commands"));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.execute(sender, args, label);
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "no-player");
            return true;
        }
        if (isHelpRequest(args)) {
            help(player, label, helpPage(args));
            return true;
        }
        String sub = args.length == 0 ? "main" : args[0].toLowerCase(Locale.ROOT);
        if (!knownSubcommand(sub)) {
            help(player, label, 1);
            return true;
        }
        String disabledFeature = disabledFeatureFor(sub);
        if (disabledFeature != null) {
            messages.send(player, "feature-disabled", Map.of("feature", disabledFeature));
            return true;
        }
        if (isHelpRequest(args)) {
            help(player, label, helpPage(args));
            return true;
        }
        boolean readOnly = readOnlyCommand(sub);
        Optional<FactoryContext> context = readOnly ? islands.existingContext(player) : islands.context(player);
        if (context.isEmpty()) {
            messages.send(player, "no-island");
            return true;
        }
        FactoryContext factoryContext = context.get();
        FactoryIsland island = factoryContext.factoryIsland();
        if (!readOnly && enabled("resource-nodes")) {
            ensureResourceNodes(player, factoryContext);
        }
        if (!readOnly && enabled("maintenance")) {
            maintenance.chargeIfDue(island, player, factoryContext.islandRef().raw());
            islands.save(island);
        }
        switch (sub) {
            case "help", "list", "commands", "command", "command-list", "명령어", "명령어목록" -> help(player, label, helpPage(args));
            case "main" -> {
                if (requireFeature(player, "gui")) {
                    gui.openMain(
                            player,
                            island,
                            enabled("machines") ? machines.byIsland(island.islandUuid()).size() : 0,
                            enabled("machines") ? power.state(island.islandUuid()) : null,
                            boosts.boosts(island.islandUuid())
                    );
                }
            }
            case "status" -> status(player, island);
            case "machines" -> {
                if (requireFeature(player, "machines")) {
                    messages.send(player, "machines-count", Map.of("count", String.valueOf(machines.byIsland(island.islandUuid()).size())));
                }
            }
            case "storage" -> {
                if (requireFeature(player, "storage") && requireFeature(player, "gui")) {
                    gui.openStorage(player, island);
                }
            }
            case "deposit" -> {
                if (requireFeature(player, "storage")) {
                    depositHand(player, island);
                }
            }
            case "withdraw" -> {
                if (requireFeature(player, "storage")) {
                    withdraw(player, island, args);
                }
            }
            case "market" -> {
                if (requireFeature(player, "market") && requireFeature(player, "storage") && requireFeature(player, "gui")) {
                    gui.openMarket(player, island, market);
                }
            }
            case "contracts" -> {
                if (!requireFeature(player, "contracts")) {
                    return true;
                }
                if (!requireFeature(player, "storage")) {
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("complete")) {
                    contracts.completeAny(island, player).ifPresentOrElse(active -> {
                        refreshMaintenanceStatus(island);
                        messages.send(player, "contract-completed", Map.of("contract", active.template().id()));
                    }, () -> messages.send(player, "contract-requirements-missing"));
                } else if (requireFeature(player, "gui")) {
                    gui.openContracts(player, island, contracts);
                }
            }
            case "research" -> {
                if (requireFeature(player, "research")) {
                    research(player, island, args);
                }
            }
            case "emergency" -> {
                if (!requireFeature(player, "contracts")) {
                    return true;
                }
                if (!requireFeature(player, "maintenance")) {
                    return true;
                }
                if (!requireFeature(player, "storage")) {
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("complete")) {
                    if (contracts.completeEmergency(island, player)) {
                        refreshMaintenanceStatus(island);
                        islands.save(island);
                        messages.send(player, "emergency-contract-completed");
                    } else {
                        messages.send(player, "emergency-contract-unavailable");
                    }
                } else {
                    showEmergency(player, island);
                }
            }
            case "node" -> {
                if (!requireFeature(player, "resource-nodes")) {
                    return true;
                }
                if (args.length > 1 && args[1].equalsIgnoreCase("scan")) {
                    nodes.generateIfMissing(island.islandUuid(), player.getLocation(), location -> isInsideIsland(location, island))
                            .forEach(node -> messages.send(player, "node-scan-result",
                                    Map.of("item", node.resourceId())));
                }
            }
            case "sell" -> {
                if (requireFeature(player, "market") && requireFeature(player, "storage")) {
                    sell(player, island, args);
                }
            }
            case "repair" -> {
                if (requireFeature(player, "maintenance") && requireFeature(player, "storage")) {
                    repairTarget(player, island);
                }
            }
            default -> help(player, label, 1);
        }
        return true;
    }

    private String[] rootAdminArgs(String[] args) {
        String[] adminArgs = new String[args.length + 1];
        adminArgs[0] = "admin";
        System.arraycopy(args, 0, adminArgs, 1, args.length);
        return adminArgs;
    }

    private boolean isInsideIsland(Location location, FactoryIsland island) {
        return skyblock.getIslandAt(location)
                .map(ref -> ref.islandUuid().equals(island.islandUuid()))
                .orElse(false);
    }

    private boolean readOnlyCommand(String subcommand) {
        return subcommand.equals("status") || subcommand.equals("machines");
    }

    private boolean knownSubcommand(String subcommand) {
        return switch (subcommand) {
            case "main", "status", "machines", "storage", "deposit", "withdraw", "market", "contracts",
                    "research", "emergency", "node", "sell", "repair" -> true;
            default -> false;
        };
    }

    private String disabledFeatureFor(String subcommand) {
        return switch (subcommand) {
            case "main" -> enabled("gui") ? null : "gui";
            case "machines" -> enabled("machines") ? null : "machines";
            case "storage" -> !enabled("storage") ? "storage" : (!enabled("gui") ? "gui" : null);
            case "deposit", "withdraw" -> enabled("storage") ? null : "storage";
            case "market" -> !enabled("market") ? "market" : (!enabled("storage") ? "storage" : (!enabled("gui") ? "gui" : null));
            case "contracts" -> !enabled("contracts") ? "contracts" : (!enabled("storage") ? "storage" : null);
            case "research" -> enabled("research") ? null : "research";
            case "emergency" -> !enabled("contracts") ? "contracts" : (!enabled("maintenance") ? "maintenance" : (!enabled("storage") ? "storage" : null));
            case "node" -> enabled("resource-nodes") ? null : "resource-nodes";
            case "sell" -> !enabled("market") ? "market" : (!enabled("storage") ? "storage" : null);
            case "repair" -> !enabled("maintenance") ? "maintenance" : (!enabled("storage") ? "storage" : null);
            default -> null;
        };
    }

    private void ensureResourceNodes(Player player, FactoryContext context) {
        FactoryIsland island = context.factoryIsland();
        Location origin = skyblock.getIslandCenter(context.islandRef()).orElse(player.getLocation());
        nodes.generateIfMissing(island.islandUuid(), origin, location -> isInsideIsland(location, island));
    }

    private void sell(Player player, FactoryIsland island, String[] args) {
        if (!requireFeature(player, "market") || !requireFeature(player, "storage")) {
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("hand")) {
            sellHand(player, island);
            return;
        }
        if (args.length < 3) {
            messages.send(player, "sell-usage");
            return;
        }
        String itemId = args[1];
        long amount = parseLong(args, 2, 0);
        market.sell(island, player, itemId, amount)
                .ifPresentOrElse(result -> {
                            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
                            if (result.debtRepaid() > 0) {
                                refreshMaintenanceStatus(island);
                                messages.send(player, "debt-repaid", Map.of("amount", String.valueOf(result.debtRepaid())));
                            }
                        },
                        () -> messages.send(player, "cannot-sell"));
    }

    private void sellHand(Player player, FactoryIsland island) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(player, "hold-item-first");
            return;
        }
        Optional<String> resolvedItemId = itemIdForHand(hand);
        if (resolvedItemId.isEmpty()) {
            messages.send(player, "unknown-item");
            return;
        }
        String itemId = resolvedItemId.get();
        int amount = hand.getAmount();
        market.sellDirect(island, player, itemId, amount).ifPresentOrElse(result -> {
            hand.setAmount(0);
            messages.send(player, "sold", Map.of("item", itemId, "amount", String.valueOf(amount), "money", String.valueOf(result.paidToPlayer())));
            if (result.debtRepaid() > 0) {
                refreshMaintenanceStatus(island);
                messages.send(player, "debt-repaid", Map.of("amount", String.valueOf(result.debtRepaid())));
            }
        }, () -> messages.send(player, "hand-item-cannot-sell"));
    }

    private void refreshMaintenanceStatus(FactoryIsland island) {
        if (!enabled("maintenance")) {
            return;
        }
        maintenance.updateStatus(island);
        islands.save(island);
    }

    private void depositHand(Player player, FactoryIsland island) {
        if (!requireFeature(player, "storage")) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            messages.send(player, "hold-item-first");
            return;
        }
        Optional<String> resolvedItemId = itemIdForHand(hand);
        if (resolvedItemId.isEmpty()) {
            messages.send(player, "unknown-item");
            return;
        }
        String itemId = resolvedItemId.get();
        long amount = hand.getAmount();
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.add(itemId, amount)) {
            messages.send(player, "storage-full");
            return;
        }
        hand.setAmount(0);
        storage.save(inventory);
        messages.send(player, "deposited", Map.of("item", itemId, "amount", String.valueOf(amount)));
    }

    private Optional<String> itemIdForHand(ItemStack stack) {
        if (itemFactory.isMachineItem(stack)) {
            return Optional.empty();
        }
        Optional<String> pdcItemId = itemFactory.factoryItemId(stack);
        if (pdcItemId.isPresent()) {
            return pdcItemId;
        }
        return Optional.of(items.itemIdForMaterial(stack.getType())
                .orElseGet(() -> stack.getType().name().toLowerCase(Locale.ROOT)));
    }

    private void withdraw(Player player, FactoryIsland island, String[] args) {
        if (!requireFeature(player, "storage")) {
            return;
        }
        if (args.length < 3) {
            messages.send(player, "withdraw-usage");
            return;
        }
        String itemId = args[1];
        long amount = parseLong(args, 2, 0);
        if (amount <= 0 || amount > Integer.MAX_VALUE) {
            messages.send(player, "invalid-amount");
            return;
        }
        if (items.get(itemId).map(ItemDefinition::virtualOnly).orElse(false)) {
            messages.send(player, "virtual-only-withdraw");
            return;
        }
        var inventory = storage.islandStorage(island.islandUuid());
        if (!inventory.remove(itemId, amount)) {
            messages.send(player, "not-enough-storage");
            return;
        }
        long returned = giveVirtualItem(player, itemId, amount);
        if (returned > 0) {
            inventory.add(itemId, returned);
            messages.send(player, "inventory-full");
        }
        storage.save(inventory);
        messages.send(player, "withdrew", Map.of("item", itemId, "amount", String.valueOf(amount - returned)));
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

    private void status(Player player, FactoryIsland island) {
        messages.send(player, "status-tier", Map.of("tier", String.valueOf(island.tier())));
        if (enabled("research")) {
            messages.send(player, "status-research", Map.of("research", String.valueOf(island.researchPoints())));
        }
        if (enabled("maintenance")) {
            messages.send(player, "status-debt", Map.of("debt", String.valueOf(island.maintenanceDebt()), "status", island.maintenanceStatus().name()));
        }
        if (enabled("machines")) {
            messages.send(player, "status-machines", Map.of("count", String.valueOf(machines.byIsland(island.islandUuid()).size())));
        }
        if (enabled("storage")) {
            messages.send(player, "status-storage", Map.of("used", storage.findIslandStorage(island.islandUuid())
                    .map(inventory -> String.valueOf(inventory.used()))
                    .orElse("0")));
        }
        var boost = boosts.boosts(island.islandUuid());
        boolean contractBoostVisible = enabled("contracts") && enabled("storage");
        if (enabled("machines") || contractBoostVisible) {
            messages.send(player, "status-boosts", Map.of(
                    "agriculture", enabled("machines") ? NumberFormatter.ratio(boost.agricultureBoost()) : "0",
                    "machine", enabled("machines") ? String.valueOf(boost.factorySlotBonus()) : "0",
                    "contract", contractBoostVisible ? String.valueOf(boost.contractSlotBonus()) : "0"));
        }
    }

    private void research(Player player, FactoryIsland island, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("unlock")) {
            ResearchService.UnlockResult result = research.unlock(island, player, args[2]);
            if (result == ResearchService.UnlockResult.UNLOCKED) {
                islands.save(island);
            }
            messages.send(player, "research-unlock-result", Map.of("result", result.name()));
            return;
        }
        if (requireFeature(player, "gui")) {
            gui.openResearch(player, island, research);
        }
    }

    private void showEmergency(Player player, FactoryIsland island) {
        contracts.emergencyTemplate().ifPresentOrElse(template -> {
            messages.send(player, "emergency-contract", Map.of("contract", template.id()));
            messages.send(player, "status-debt", Map.of("debt", String.valueOf(island.maintenanceDebt()), "status", island.maintenanceStatus().name()));
            messages.send(player, "emergency-required", Map.of("required", template.required().toString()));
            messages.send(player, "emergency-rewards", Map.of(
                    "money", String.valueOf(template.money()),
                    "research", String.valueOf(template.research()),
                    "reputation", String.valueOf(template.reputation()),
                    "debt", String.valueOf(template.debtRelief()),
                    "items", template.itemRewards().toString()));
            messages.send(player, "emergency-used", Map.of("used", String.valueOf(contracts.emergencyUsedToday(island)),
                    "limit", String.valueOf(contracts.emergencyDailyLimit())));
            messages.send(player, "emergency-complete-help");
        }, () -> messages.send(player, "no-emergency-contract"));
    }

    private void help(Player player, String label, int page) {
        List<String> commands = visibleHelpCommands(label, player);
        CommandListPolicy.Page commandPage = CommandListPolicy.page(commands, page, label + " command list", commandListPageSize);
        player.sendMessage(messages.raw("command-list-title", Map.of("page", String.valueOf(commandPage.page()), "pages", String.valueOf(commandPage.pages()))) + " commands=" + commandPage.rangeSummary());
        for (String command : commandPage.entries()) {
            player.sendMessage(messages.raw("command-list-entry", Map.of("command", command)));
        }
        if (commandPage.previousCommand() != null) {
            player.sendMessage(messages.raw("command-list-entry", Map.of("command", commandPage.previousCommand())));
        }
        if (commandPage.nextCommand() != null) {
            player.sendMessage(messages.raw("command-list-entry", Map.of("command", commandPage.nextCommand())));
        }
    }

    private List<String> visibleHelpCommands(String label, CommandSender viewer) {
        List<String> values = new ArrayList<>();
        for (String command : HELP_COMMANDS) {
            if (commandRequiresDisabledFeature(command, viewer)) {
                continue;
            }
            values.add(command.replaceFirst("^factory", label));
        }
        return values;
    }

    private boolean commandRequiresDisabledFeature(String command, CommandSender viewer) {
        return command.contains(" main") && !enabled("gui")
                || command.contains(" storage") && (!enabled("storage") || !enabled("gui"))
                || command.contains(" machines") && !enabled("machines")
                || (command.contains(" deposit") || command.contains(" withdraw")) && !enabled("storage")
                || command.endsWith(" market") && (!enabled("market") || !enabled("storage") || !enabled("gui"))
                || command.contains(" sell") && (!enabled("market") || !enabled("storage"))
                || command.endsWith(" contracts") && (!enabled("contracts") || !enabled("storage") || !enabled("gui"))
                || command.contains(" contracts complete") && (!enabled("contracts") || !enabled("storage"))
                || command.contains(" emergency") && (!enabled("contracts") || !enabled("maintenance") || !enabled("storage"))
                || command.contains(" research") && !enabled("research")
                || command.contains(" node") && !enabled("resource-nodes")
                || command.contains(" repair") && (!enabled("maintenance") || !enabled("storage"))
                || command.contains(" admin ") && (viewer == null || !viewer.hasPermission("satisskyfactory.admin"));
    }

    private boolean isHelpRequest(String[] args) {
        if (args.length == 0) {
            return false;
        }
        String first = args[0].toLowerCase(Locale.ROOT);
        if (isCommandListRoot(first) && args.length > 1 && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return true;
        }
        return first.equals("help") || first.equals("list") || first.equals("commands") || first.equals("command") || first.equals("command-list")
                || first.equals("명령어") || first.equals("명령어목록");
    }

    private int helpPage(String[] args) {
        if (args.length > 2 && isCommandListRoot(args[0]) && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return (int) parseLong(args, 2, 1);
        }
        if (args.length > 1) {
            return (int) parseLong(args, 1, 1);
        }
        return 1;
    }

    private boolean isCommandListRoot(String value) {
        return value.equalsIgnoreCase("command")
                || value.equalsIgnoreCase("list")
                || value.equalsIgnoreCase("commands")
                || value.equalsIgnoreCase("command-list")
                || value.equals("명령어")
                || value.equals("명령어목록");
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

    private boolean requireFeature(Player player, String feature) {
        if (enabled(feature)) {
            return true;
        }
        messages.send(player, "feature-disabled", Map.of("feature", feature));
        return false;
    }

    private void repairTarget(Player player, FactoryIsland island) {
        if (!requireFeature(player, "maintenance") || !requireFeature(player, "storage")) {
            return;
        }
        Block block = player.getTargetBlockExact(8);
        if (block == null || block.getType() == Material.AIR) {
            messages.send(player, "no-target-machine");
            return;
        }
        machines.at(block.getLocation()).ifPresentOrElse(machine -> {
            if (!machine.islandUuid().equals(island.islandUuid())) {
                messages.send(player, "machine-wrong-island");
                return;
            }
            if (!consumeRepairParts(island, machine)) {
                messages.send(player, "repair-requires", Map.of("cost", repairCostText(machine)));
                return;
            }
            repair(machine);
            messages.send(player, "machine-repaired");
        }, () -> messages.send(player, "no-machine-here"));
    }

    private boolean consumeRepairParts(FactoryIsland island, MachineInstance machine) {
        var inventory = storage.islandStorage(island.islandUuid());
        Map<String, Long> cost = maintenance.repairCost(machine.status() == MachineStatus.BROKEN);
        if (cost.entrySet().stream().anyMatch(entry -> inventory.amount(entry.getKey()) < entry.getValue())) {
            return false;
        }
        cost.forEach(inventory::remove);
        storage.save(inventory);
        return true;
    }

    private String repairCostText(MachineInstance machine) {
        Map<String, Long> cost = maintenance.repairCost(machine.status() == MachineStatus.BROKEN);
        if (cost.isEmpty()) {
            return messages.raw("no-materials");
        }
        return cost.entrySet().stream()
                .map(entry -> entry.getKey() + " x" + entry.getValue())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse(messages.raw("no-materials"));
    }

    private void repair(MachineInstance machine) {
        machine.wear(0.0);
        machine.status(MachineStatus.SLEEPING);
        machines.save(machine);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!enabled("commands")) {
            return new ArrayList<>();
        }
        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            values.add("help");
            values.add("list");
            values.add("commands");
            values.add("command");
            values.add("command-list");
            values.add("명령어");
            values.add("명령어목록");
            values.add("status");
            if (enabled("gui")) {
                values.add("main");
            }
            if (enabled("storage") && enabled("gui")) {
                values.add("storage");
            }
            if (enabled("machines")) {
                values.add("machines");
            }
            if (enabled("storage")) {
                values.add("deposit");
                values.add("withdraw");
            }
            if (enabled("contracts") && enabled("storage")) {
                values.add("contracts");
            }
            if (enabled("contracts") && enabled("maintenance") && enabled("storage")) {
                values.add("emergency");
            }
            if (enabled("market") && enabled("storage") && enabled("gui")) {
                values.add("market");
            }
            if (enabled("market") && enabled("storage")) {
                values.add("sell");
            }
            if (enabled("research")) {
                values.add("research");
            }
            if (enabled("resource-nodes")) {
                values.add("node");
            }
            if (enabled("maintenance") && enabled("storage")) {
                values.add("repair");
            }
            if (sender.hasPermission("satisskyfactory.admin")) {
                values.add("admin");
            }
            return filter(values, args[0]);
        }
        if (args.length == 2 && isCommandListRoot(args[0])) {
            List<String> values = new ArrayList<>();
            values.add("list");
            values.addAll(helpPageSuggestions(sender));
            return filter(values, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
            return filter(helpPageSuggestions(sender), args[1]);
        }
        if (args.length == 3 && isCommandListRoot(args[0]) && (args[1].equalsIgnoreCase("list") || args[1].equals("목록"))) {
            return filter(helpPageSuggestions(sender), args[2]);
        }
        if ((args[0].equalsIgnoreCase("sell") && !enabled("market"))
                || ((args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("market")) && !enabled("storage"))
                || (args[0].equalsIgnoreCase("market") && !enabled("gui"))
                || ((args[0].equalsIgnoreCase("withdraw") || args[0].equalsIgnoreCase("deposit")) && !enabled("storage"))
                || (args[0].equalsIgnoreCase("contracts") && (!enabled("contracts") || !enabled("storage")))
                || (args[0].equalsIgnoreCase("emergency") && (!enabled("contracts") || !enabled("maintenance") || !enabled("storage")))
                || (args[0].equalsIgnoreCase("repair") && (!enabled("maintenance") || !enabled("storage")))
                || (args[0].equalsIgnoreCase("node") && !enabled("resource-nodes"))
                || (args[0].equalsIgnoreCase("research") && !enabled("research"))) {
            return new ArrayList<>();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            List<String> values = new ArrayList<>();
            values.add("hand");
            values.addAll(market.prices().keySet().stream().sorted().toList());
            return filter(values, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("sell") && !args[1].equalsIgnoreCase("hand")) {
            return filter(amountSuggestions(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("withdraw")) {
            return filter(storedItemIds(sender), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("withdraw")) {
            return filter(amountSuggestions(), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("contracts")) {
            return filter(List.of("complete"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("emergency")) {
            return filter(List.of("complete"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("node")) {
            return filter(List.of("scan"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("research")) {
            return filter(List.of("unlock"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.complete(sender, args);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("research") && args[1].equalsIgnoreCase("unlock")) {
            return filter(research.all().keySet().stream().toList(), args[2]);
        }
        if (args.length > 1 && args[0].equalsIgnoreCase("admin")) {
            return adminCommand.complete(sender, args);
        }
        return new ArrayList<>();
    }

    private List<String> helpPageSuggestions(CommandSender sender) {
        int maxPage = CommandListPolicy.pages(visibleHelpCommands("factory", sender).size(), commandListPageSize);
        List<String> values = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            values.add(String.valueOf(page));
        }
        return values;
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }

    private List<String> storedItemIds(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return itemIds();
        }
        return islands.existingContext(player)
                .flatMap(context -> storage.findIslandStorage(context.factoryIsland().islandUuid()))
                .map(inventory -> inventory.items().keySet().stream()
                        .sorted()
                        .toList())
                .filter(values -> !values.isEmpty())
                .orElseGet(this::itemIds);
    }

    private List<String> itemIds() {
        return items.all().keySet().stream().sorted().toList();
    }

    private List<String> amountSuggestions() {
        return List.of("1", "8", "16", "32", "64", "256", "1024");
    }

}
