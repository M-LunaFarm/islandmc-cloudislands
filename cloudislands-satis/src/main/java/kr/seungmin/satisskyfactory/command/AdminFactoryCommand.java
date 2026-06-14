package kr.seungmin.satisskyfactory.command;

import kr.seungmin.satisskyfactory.config.MessageService;
import kr.seungmin.satisskyfactory.database.DatabaseService;
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

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AdminFactoryCommand {
    private static final int HELP_PAGE_SIZE = 12;
    private static final List<String> FEATURE_KEYS = List.of(
            "commands",
            "machines",
            "storage",
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
            "placeholders",
            "migration",
            "addon-state"
    );
    private static final List<String> HELP_COMMANDS = List.of(
            "factory admin help [page]",
            "factory admin command list [page]",
            "factory admin reload",
            "factory admin features",
            "factory admin integration",
            "factory admin migration",
            "factory admin migration scan <sqlitePath>",
            "factory admin migration dryrun <sqlitePath>",
            "factory admin migration verify <sqlitePath>",
            "factory admin migration import <sqlitePath>",
            "factory admin migration rollback",
            "factory admin state",
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
    private final DatabaseService database;
    private final Predicate<String> featureEnabled;
    private final Supplier<Map<String, String>> integrationMetadata;
    private final Supplier<Map<String, String>> addonState;
    private final Function<UUID, Map<String, String>> addonIslandState;
    private final Runnable reload;

    public AdminFactoryCommand(FactoryIslandService islands, MachineService machines, MachineDefinitionService definitions,
                               StorageService storage, ResourceNodeService nodes, SkyblockProvider skyblock,
                               MaintenanceService maintenance, ResearchService research, PowerNetworkService power,
                               CustomItemFactory itemFactory, ItemRegistry items,
                               MessageService messages, DatabaseService database, Predicate<String> featureEnabled,
                               Supplier<Map<String, String>> integrationMetadata,
                               Supplier<Map<String, String>> addonState,
                               Function<UUID, Map<String, String>> addonIslandState,
                               Runnable reload) {
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
        this.database = database;
        this.featureEnabled = featureEnabled;
        this.integrationMetadata = integrationMetadata;
        this.addonState = addonState;
        this.addonIslandState = addonIslandState;
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
        String disabledFeature = disabledFeatureFor(subcommand);
        if (disabledFeature != null) {
            messages.send(sender, "feature-disabled", Map.of("feature", disabledFeature));
            return true;
        }
        switch (subcommand) {
            case "help", "commands", "command", "command-list", "명령어", "명령어목록" -> help(sender, helpPage(args));
            case "reload" -> {
                reload.run();
                messages.send(sender, "reloaded");
            }
            case "features" -> showFeatures(sender);
            case "integration" -> showIntegration(sender);
            case "migration" -> {
                if (requireFeature(sender, "migration")) {
                    handleMigration(sender, args);
                }
            }
            case "state" -> {
                if (requireFeature(sender, "addon-state")) {
                    showAddonState(sender);
                }
            }
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

    private String disabledFeatureFor(String subcommand) {
        return switch (subcommand) {
            case "migration" -> enabled("migration") ? null : "migration";
            case "state" -> enabled("addon-state") ? null : "addon-state";
            case "give", "giveitem", "removehere" -> enabled("machines") ? null : "machines";
            case "addresearch" -> enabled("research") ? null : "research";
            case "setdebt", "charge", "repairhere" -> enabled("maintenance") ? null : "maintenance";
            case "gennodes" -> enabled("resource-nodes") ? null : "resource-nodes";
            case "debug" -> debugCommandsVisible() ? null : "debug";
            default -> null;
        };
    }

    private boolean debugCommandsVisible() {
        return enabled("machines")
                || enabled("storage")
                || enabled("resource-nodes")
                || enabled("maintenance")
                || enabled("addon-state");
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
            if (debugCommandsVisible()) {
                values.add("debug");
            }
            values.add("features");
            values.add("integration");
            if (enabled("migration")) {
                values.add("migration");
            }
            if (enabled("addon-state")) {
                values.add("state");
            }
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
        if (args.length == 3 && args[1].equalsIgnoreCase("migration") && enabled("migration")) {
            return filter(List.of("scan", "dryrun", "dry-run", "verify", "import", "rollback"), args[2]);
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
            return filter(debugTargets(), args[2]);
        }
        return new ArrayList<>();
    }

    private List<String> debugTargets() {
        List<String> values = new ArrayList<>();
        values.add("island");
        if (enabled("machines")) {
            values.add("networks");
        }
        return values;
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
                if (!requireFeature(sender, "storage")) {
                    return;
                }
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
        if (args[2].equalsIgnoreCase("networks") && !requireFeature(sender, "machines")) {
            return;
        }
        islands.existingContext(player).ifPresent(context -> {
            if (args[2].equalsIgnoreCase("island")) {
                messages.send(sender, "debug-island", Map.of("island", context.factoryIsland().islandUuid().toString()));
            } else if (args[2].equalsIgnoreCase("networks")) {
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
        Map<String, String> details = featureDetails();
        List.of("configured-features", "effective-features", "operational-features", "dependency-disabled-features", "feature-warnings").forEach(key -> {
            String value = details.get(key);
            if (value != null && !value.isBlank()) {
                sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", key,
                        "value", value
                )));
            }
        });
    }

    private Map<String, String> featureDetails() {
        try {
            Map<String, String> state = addonState == null ? Map.of() : addonState.get();
            if (state != null && (!blank(state.get("configured-features")) || !blank(state.get("effective-features")) || !blank(state.get("operational-features")) || !blank(state.get("feature-warnings")))) {
                return state;
            }
        } catch (RuntimeException ignored) {
        }
        try {
            Map<String, String> metadata = integrationMetadata == null ? Map.of() : integrationMetadata.get();
            return metadata == null ? Map.of() : metadata;
        } catch (RuntimeException ignored) {
            return Map.of();
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void showIntegration(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-integration-title"));
        Map<String, String> metadata;
        try {
            metadata = integrationMetadata == null ? Map.of() : integrationMetadata.get();
        } catch (RuntimeException exception) {
            metadata = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
        }
        metadata.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void showMigration(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-migration-title"));
        Map<String, String> state = new LinkedHashMap<>();
        state.put("satis-schema", "3");
        state.put("satis-storage-key", "cloudislands-island-uuid");
        state.put("superior-migration-input-only", "true");
        state.put("superior-runtime-dependency", "false");
        state.put("superior-import-scan", "/ciadmin migrate-superiorskyblock2 scan [path]");
        state.put("superior-import-dryrun", "/ciadmin migrate-superiorskyblock2 dryrun [path]");
        state.put("superior-import-import", "/ciadmin migrate-superiorskyblock2 import <approvalToken>");
        state.put("superior-import-verify", "/ciadmin migrate-superiorskyblock2 verify [path]");
        state.put("superior-import-rollback", "/ciadmin migrate-superiorskyblock2 rollback [path]");
        state.put("satismc-import-scan", "/factory admin migration scan <sqlitePath>");
        state.put("satismc-import-dryrun", "/factory admin migration dryrun <sqlitePath>");
        state.put("satismc-import-verify", "/factory admin migration verify <sqlitePath>");
        state.put("satismc-import-import", "/factory admin migration import <sqlitePath>");
        state.put("satismc-import-mode", "cross-backend-sqlite-copy");
        state.put("satismc-rollback-mode", "manual-restore-from-backup");
        state.put("feature-gate", "migration=" + enabled("migration"));
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void handleMigration(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            String action = args[2].toLowerCase(Locale.ROOT);
            if (action.equals("scan") || action.equals("dryrun") || action.equals("dry-run") || action.equals("verify")) {
                scanLegacyDatabase(sender, args, action);
                return;
            }
            if (action.equals("import")) {
                importLegacyDatabase(sender, args);
                return;
            }
            if (action.equals("rollback")) {
                rollbackLegacyDatabase(sender);
                return;
            }
        }
        showMigration(sender);
    }

    private void scanLegacyDatabase(CommandSender sender, String[] args, String action) {
        if (args.length < 4) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "usage",
                    "value", "/factory admin migration " + action + " <sqlitePath>"
            )));
            return;
        }
        try {
            DatabaseService.LegacyImportPlan plan = database.scanLegacyDatabase(new File(joined(args, 3)));
            sender.sendMessage(messages.raw("admin-migration-title"));
            Map<String, String> state = new LinkedHashMap<>();
            state.put("source", plan.sourcePath());
            state.put("mode", action.equals("scan") ? "scan-only" : (action.equals("verify") ? "verify-no-write" : "dryrun-no-write"));
            state.put("importable-rows", String.valueOf(plan.importableRows()));
            state.put("importable-tables", String.valueOf(plan.importableTables()));
            state.put("skipped-tables", String.valueOf(plan.skippedTables()));
            plan.tables().forEach(table -> state.put("table." + table.table(),
                    (table.importable() ? "ready" : "skip") + ",rows=" + table.sourceRows() + ",reason=" + table.reason()));
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                            "key", entry.getKey(),
                            "value", entry.getValue()
                    ))));
        } catch (RuntimeException exception) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "error",
                    "value", exception.getMessage() == null ? "unknown" : exception.getMessage()
            )));
        }
    }

    private void importLegacyDatabase(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "usage",
                    "value", "/factory admin migration import <sqlitePath>"
            )));
            return;
        }
        try {
            DatabaseService.LegacyImportResult result = database.importLegacyDatabase(new File(joined(args, 3)));
            reload.run();
            sender.sendMessage(messages.raw("admin-migration-title"));
            Map<String, String> state = new LinkedHashMap<>();
            state.put("source", result.sourcePath());
            state.put("copied-rows", String.valueOf(result.copiedRows()));
            state.put("copied-tables", String.join(",", result.copiedTables()));
            state.put("skipped-tables", result.skippedTables().isEmpty() ? "none" : String.join(",", result.skippedTables()));
            state.put("mode", "cross-backend-sqlite-copy");
            state.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                            "key", entry.getKey(),
                            "value", entry.getValue()
                    ))));
        } catch (RuntimeException exception) {
            sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                    "key", "error",
                    "value", exception.getMessage() == null ? "unknown" : exception.getMessage()
            )));
        }
    }

    private void rollbackLegacyDatabase(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-migration-title"));
        Map<String, String> state = new LinkedHashMap<>();
        state.put("mode", "manual-restore-from-backup");
        state.put("automatic-delete", "false");
        state.put("reason", "rollback must not delete mixed live CloudIslands/Satis data automatically");
        state.put("safe-path", "restore database backup, then run /factory admin migration verify <sqlitePath>");
        state.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
    }

    private void showAddonState(CommandSender sender) {
        sender.sendMessage(messages.raw("admin-integration-title"));
        Map<String, String> state;
        try {
            state = addonState == null ? Map.of() : addonState.get();
        } catch (RuntimeException exception) {
            state = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
        }
        Map<String, String> visible = new LinkedHashMap<>(state);
        if (integrationMetadata != null) {
            try {
                Map<String, String> metadata = integrationMetadata.get();
                List.of(
                        "database-configured-backend",
                        "database-active-backend",
                        "database-attempted-backends",
                        "database-supported-backends",
                        "database-fallback-reason",
                        "database-fallback-enabled",
                        "database-fallback-order",
                        "database-config-source",
                        "database-config-env",
                        "database-jdbc-source",
                        "database-jdbc-env",
                        "database-credentials-source",
                        "database-credentials-env",
                        "database-pool-source",
                        "database-pool-env",
                        "database-fallback-source",
                        "database-fallback-env",
                        "database-scope",
                        "database-shared",
                        "runtime-commands-registered",
                        "runtime-machine-listener-registered",
                        "runtime-gui-listener-registered",
                        "runtime-lifecycle-listener-registered",
                        "runtime-placeholder-registered",
                        "runtime-machine-ticker-running",
                        "runtime-maintenance-ticker-running",
                        "runtime-dirty-save-running",
                        "runtime-core-api-state-writer",
                        "addon-state-bulk-save-api",
                        "addon-state-bulk-save-global-endpoint",
                        "addon-state-bulk-save-island-endpoint",
                        "addon-state-table-key-value-bulk-save-global-endpoint",
                        "addon-state-table-key-value-bulk-save-island-endpoint",
                        "addon-state-bulk-save-methods",
                        "core-api-table-save-mode"
                ).forEach(key -> {
                    String value = metadata.get(key);
                    if (value != null && !value.isBlank()) {
                        visible.put("local." + key, value);
                    }
                });
            } catch (RuntimeException exception) {
                visible.put("local.database-metadata-error", exception.getMessage() == null ? "unknown" : exception.getMessage());
            }
        }
        if (database != null) {
            visible.put("local.database-active-backend", database.activeBackend().name());
            visible.put("local.database-attempted-backends", database.attemptedBackends().stream()
                    .map(DatabaseService.StorageBackend::name)
                    .reduce((left, right) -> left + "," + right)
                    .orElse("none"));
            visible.put("local.database-fallback-reason", database.fallbackReason());
            visible.put("local.database-description", database.databaseDescription());
        }
        if (sender instanceof Player player && addonIslandState != null) {
            islands.context(player).ifPresent(context -> {
                Map<String, String> islandState;
                try {
                    islandState = addonIslandState.apply(context.factoryIsland().islandUuid());
                } catch (RuntimeException exception) {
                    islandState = Map.of("status", "unavailable", "error", exception.getMessage() == null ? "unknown" : exception.getMessage());
                }
                islandState.forEach((key, value) -> visible.put("island." + key, value));
            });
        }
        visible.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sender.sendMessage(messages.raw("admin-integration-entry", Map.of(
                        "key", entry.getKey(),
                        "value", entry.getValue()
                ))));
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
                || command.contains(" migration") && !enabled("migration")
                || command.contains(" state") && !enabled("addon-state")
                || command.contains(" debug ") && !debugCommandsVisible()
                || command.contains(" debug networks") && !enabled("machines");
    }

    private int helpPage(String[] args) {
        if (args.length > 3 && isCommandListRoot(args[1]) && (args[2].equalsIgnoreCase("list") || args[2].equals("목록"))) {
            return (int) parseLong(args, 3, 1);
        }
        if (args.length > 2) {
            return (int) parseLong(args, 2, 1);
        }
        return 1;
    }

    private boolean isCommandListRoot(String value) {
        return value.equalsIgnoreCase("command")
                || value.equalsIgnoreCase("commands")
                || value.equalsIgnoreCase("command-list")
                || value.equals("명령어")
                || value.equals("명령어목록");
    }

    private String joined(String[] args, int fromIndex) {
        if (args.length <= fromIndex) {
            return "";
        }
        return String.join(" ", java.util.Arrays.copyOfRange(args, fromIndex, args.length)).trim();
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
